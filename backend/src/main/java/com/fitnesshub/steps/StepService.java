package com.fitnesshub.steps;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.dto.LogStepsRequest;
import com.fitnesshub.steps.dto.StepDashboardDto;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class StepService {

    private static final int DEFAULT_GOAL = 10000;

    private final StepEntryRepository stepEntryRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public StepService(StepEntryRepository stepEntryRepository,
                        ClientProfileRepository clientProfileRepository,
                        UserRepository userRepository,
                        CurrentUser currentUser,
                        AuthorizationService authorizationService,
                        AuditService auditService) {
        this.stepEntryRepository = stepEntryRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public StepEntry log(LogStepsRequest request) {
        UUID clientId = currentUser.id();
        LocalDate date = request.date() == null ? LocalDate.now(zoneFor(clientId)) : request.date();
        StepEntry entry = stepEntryRepository.findByClientIdAndDate(clientId, date)
                .orElseGet(() -> new StepEntry(clientId, request.steps(), date, StepSource.MANUAL));
        entry.setSteps(request.steps());
        entry = stepEntryRepository.save(entry);
        auditService.record(clientId, AuditAction.STEPS_LOGGED, "StepEntry", entry.getId());
        return entry;
    }

    @Transactional(readOnly = true)
    public Page<StepEntry> history(UUID requestedClientId, Pageable pageable) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return stepEntryRepository.findByClientIdOrderByDateDesc(clientId, pageable);
    }

    /** The step target currently in effect: the coach's override if set, otherwise the platform default of 10,000. */
    public int activeGoal(ClientProfile profile) {
        if (profile != null && profile.getDailyStepTarget() != null) {
            return profile.getDailyStepTarget();
        }
        return DEFAULT_GOAL;
    }

    @Transactional(readOnly = true)
    public StepDashboardDto dashboard(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        ZoneId zone = zoneFor(clientId);
        LocalDate today = LocalDate.now(zone);

        int todaySteps = stepEntryRepository.findByClientIdAndDate(clientId, today)
                .map(StepEntry::getSteps).orElse(0);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<StepEntry> thisWeek = stepEntryRepository
                .findByClientIdAndDateBetweenOrderByDateAsc(clientId, weekStart, today);
        long weeklyTotal = thisWeek.stream().mapToLong(StepEntry::getSteps).sum();
        BigDecimal weeklyAverage = average(thisWeek);

        List<StepEntry> last7Days = stepEntryRepository
                .findByClientIdAndDateBetweenOrderByDateAsc(clientId, today.minusDays(6), today);
        BigDecimal sevenDayAverage = average(last7Days);

        ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);
        int goal = activeGoal(profile);
        BigDecimal completionPct = goal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(todaySteps).divide(BigDecimal.valueOf(goal), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);

        return new StepDashboardDto(todaySteps, weeklyAverage, sevenDayAverage, weeklyTotal, goal, completionPct);
    }

    private BigDecimal average(List<StepEntry> entries) {
        if (entries.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long sum = entries.stream().mapToLong(StepEntry::getSteps).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(entries.size()), 1, RoundingMode.HALF_UP);
    }

    private ZoneId zoneFor(UUID clientId) {
        User user = userRepository.findById(clientId).orElse(null);
        try {
            return user == null ? ZoneId.of("UTC") : ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
