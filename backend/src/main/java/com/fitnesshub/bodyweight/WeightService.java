package com.fitnesshub.bodyweight;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.bodyweight.dto.LogWeightRequest;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.bodyweight.dto.WeightTrendPointDto;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class WeightService {

    private final WeightEntryRepository weightEntryRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public WeightService(WeightEntryRepository weightEntryRepository,
                          ClientProfileRepository clientProfileRepository,
                          UserRepository userRepository,
                          CurrentUser currentUser,
                          AuthorizationService authorizationService,
                          AuditService auditService) {
        this.weightEntryRepository = weightEntryRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public WeightEntry log(LogWeightRequest request) {
        UUID clientId = currentUser.id();
        WeightEntry entry = new WeightEntry(clientId, request.weight(),
                request.recordedAt() == null ? Instant.now() : request.recordedAt());
        entry.setNotes(request.notes());
        entry = weightEntryRepository.save(entry);
        auditService.record(clientId, AuditAction.WEIGHT_LOGGED, "WeightEntry", entry.getId());
        return entry;
    }

    @Transactional(readOnly = true)
    public Page<WeightEntry> history(UUID requestedClientId, Pageable pageable) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return weightEntryRepository.findByClientIdOrderByRecordedAtDesc(clientId, pageable);
    }

    @Transactional(readOnly = true)
    public WeightDashboardDto dashboard(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        ZoneId zone = zoneFor(clientId);
        List<WeightEntry> all = weightEntryRepository.findByClientIdOrderByRecordedAtAsc(clientId);
        ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);

        if (all.isEmpty()) {
            BigDecimal goal = profile == null ? null : profile.getTargetWeight();
            return new WeightDashboardDto(null, null, null, null, null,
                    "No weight entries logged yet.", null, null, null, goal);
        }

        BigDecimal current = all.get(all.size() - 1).getWeight();
        BigDecimal starting = profile != null && profile.getStartingWeight() != null
                ? profile.getStartingWeight() : all.get(0).getWeight();
        BigDecimal lowest = all.stream().map(WeightEntry::getWeight).min(Comparator.naturalOrder()).orElse(current);
        BigDecimal highest = all.stream().map(WeightEntry::getWeight).max(Comparator.naturalOrder()).orElse(current);

        LocalDate today = LocalDate.now(zone);
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        BigDecimal lastWeekAvg = weeklyAverage(all, zone, currentWeekMonday.minusWeeks(1));

        String message = lastWeekAvg == null ? "No complete weekly average yet." : null;
        BigDecimal weeklyChange = weeklyChange(all, zone, today);

        BigDecimal monthlyChange = periodChange(all, today.minusDays(30), today, today.minusDays(60), today.minusDays(30));
        BigDecimal totalChange = current.subtract(starting).setScale(2, RoundingMode.HALF_UP);

        return new WeightDashboardDto(current, starting, lowest, highest, lastWeekAvg, message, weeklyChange,
                monthlyChange, totalChange, profile == null ? null : profile.getTargetWeight());
    }

    /**
     * Last completed Monday-Sunday week's average minus the week before it, or
     * null if either week has no weigh-ins. Shared by the client's dashboard
     * and the coach's roster so both always show the same figure.
     */
    public BigDecimal weeklyChange(List<WeightEntry> entries, ZoneId zone, LocalDate today) {
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        BigDecimal lastWeekAvg = weeklyAverage(entries, zone, currentWeekMonday.minusWeeks(1));
        BigDecimal priorWeekAvg = weeklyAverage(entries, zone, currentWeekMonday.minusWeeks(2));
        if (lastWeekAvg == null || priorWeekAvg == null) {
            return null;
        }
        return lastWeekAvg.subtract(priorWeekAvg).setScale(2, RoundingMode.HALF_UP);
    }

    /** Arithmetic mean of entries within the Monday-Sunday week starting at {@code mondayOfWeek}, in {@code zone}. */
    public BigDecimal weeklyAverage(List<WeightEntry> entries, ZoneId zone, LocalDate mondayOfWeek) {
        Instant start = mondayOfWeek.atStartOfDay(zone).toInstant();
        Instant end = mondayOfWeek.plusDays(7).atStartOfDay(zone).toInstant();
        List<BigDecimal> weights = entries.stream()
                .filter(e -> !e.getRecordedAt().isBefore(start) && e.getRecordedAt().isBefore(end))
                .map(WeightEntry::getWeight)
                .toList();
        if (weights.isEmpty()) {
            return null;
        }
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(weights.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal periodChange(List<WeightEntry> all, LocalDate recentStart, LocalDate recentEnd,
                                     LocalDate priorStart, LocalDate priorEnd) {
        ZoneId zone = ZoneId.systemDefault();
        BigDecimal recentAvg = average(all, recentStart, recentEnd, zone);
        BigDecimal priorAvg = average(all, priorStart, priorEnd, zone);
        if (recentAvg == null || priorAvg == null) {
            return null;
        }
        return recentAvg.subtract(priorAvg).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<WeightEntry> all, LocalDate from, LocalDate to, ZoneId zone) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.atStartOfDay(zone).toInstant();
        List<BigDecimal> weights = all.stream()
                .filter(e -> !e.getRecordedAt().isBefore(start) && e.getRecordedAt().isBefore(end))
                .map(WeightEntry::getWeight)
                .toList();
        if (weights.isEmpty()) {
            return null;
        }
        return weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(weights.size()), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<WeightTrendPointDto> trend(UUID requestedClientId, Instant from, Instant to) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        List<WeightEntry> entries = weightEntryRepository
                .findByClientIdAndRecordedAtBetweenOrderByRecordedAtAsc(clientId, from, to);

        return entries.stream().map(entry -> {
            Instant windowStart = entry.getRecordedAt().minus(6, ChronoUnit.DAYS);
            List<BigDecimal> window = entries.stream()
                    .filter(e -> !e.getRecordedAt().isBefore(windowStart) && !e.getRecordedAt().isAfter(entry.getRecordedAt()))
                    .map(WeightEntry::getWeight)
                    .toList();
            BigDecimal avg = window.isEmpty() ? null : window.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
            return new WeightTrendPointDto(entry.getRecordedAt(), entry.getWeight(), avg);
        }).toList();
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
