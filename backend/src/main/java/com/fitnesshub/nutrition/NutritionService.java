package com.fitnesshub.nutrition;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.nutrition.dto.CalorieEstimate;
import com.fitnesshub.nutrition.dto.LogNutritionRequest;
import com.fitnesshub.nutrition.dto.NutritionDashboardDto;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepEntryRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class NutritionService {

    private final NutritionEntryRepository nutritionEntryRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final StepEntryRepository stepEntryRepository;
    private final CalorieEstimationService calorieEstimationService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public NutritionService(NutritionEntryRepository nutritionEntryRepository,
                             ClientProfileRepository clientProfileRepository,
                             StepEntryRepository stepEntryRepository,
                             CalorieEstimationService calorieEstimationService,
                             UserRepository userRepository,
                             CurrentUser currentUser,
                             AuthorizationService authorizationService,
                             AuditService auditService) {
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.calorieEstimationService = calorieEstimationService;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public NutritionEntry log(LogNutritionRequest request) {
        UUID clientId = currentUser.id();
        LocalDate date = request.date() == null ? LocalDate.now(zoneFor(clientId)) : request.date();
        NutritionEntry entry = nutritionEntryRepository.findByClientIdAndDate(clientId, date)
                .orElseGet(() -> new NutritionEntry(clientId, date, request.calories(), request.proteinGrams()));
        entry.setCalories(request.calories());
        entry.setProteinGrams(request.proteinGrams());
        entry.setCarbohydratesGrams(request.carbohydratesGrams());
        entry.setFatGrams(request.fatGrams());
        entry.setFiberGrams(request.fiberGrams());
        entry.setWaterMl(request.waterMl());
        entry.setNotes(request.notes());
        entry = nutritionEntryRepository.save(entry);
        auditService.record(clientId, AuditAction.NUTRITION_LOGGED, "NutritionEntry", entry.getId());
        return entry;
    }

    @Transactional(readOnly = true)
    public Page<NutritionEntry> history(UUID requestedClientId, Pageable pageable) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return nutritionEntryRepository.findByClientIdOrderByDateDesc(clientId, pageable);
    }

    @Transactional(readOnly = true)
    public NutritionDashboardDto dashboard(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        ZoneId zone = zoneFor(clientId);
        LocalDate today = LocalDate.now(zone);

        NutritionEntry todayEntry = nutritionEntryRepository.findByClientIdAndDate(clientId, today).orElse(null);
        BigDecimal caloriesToday = todayEntry == null ? BigDecimal.ZERO : todayEntry.getCalories();
        BigDecimal proteinToday = todayEntry == null ? BigDecimal.ZERO : todayEntry.getProteinGrams();

        ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);
        BigDecimal calorieTarget = profile == null ? null : profile.getDailyCalorieTarget();
        BigDecimal proteinTarget = profile == null ? null : profile.getDailyProteinTarget();

        BigDecimal caloriesRemaining = calorieTarget == null ? null : calorieTarget.subtract(caloriesToday);
        BigDecimal proteinRemaining = proteinTarget == null ? null : proteinTarget.subtract(proteinToday);

        List<NutritionEntry> last7 = nutritionEntryRepository
                .findByClientIdAndDateBetweenOrderByDateAsc(clientId, today.minusDays(6), today);
        BigDecimal avgCalories = average(last7.stream().map(NutritionEntry::getCalories).toList());
        BigDecimal avgProtein = average(last7.stream().map(NutritionEntry::getProteinGrams).toList());

        int todaySteps = stepEntryRepository.findByClientIdAndDate(clientId, today)
                .map(com.fitnesshub.steps.StepEntry::getSteps).orElse(0);
        CalorieEstimate estimate = calorieEstimationService.estimateForToday(clientId, todaySteps);
        BigDecimal balance = estimate.available()
                ? caloriesToday.subtract(estimate.estimatedTotalCalories())
                : null;

        return new NutritionDashboardDto(caloriesToday, calorieTarget, caloriesRemaining, proteinToday,
                proteinTarget, proteinRemaining, avgCalories, avgProtein, estimate, balance);
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(v -> v != null).toList();
        if (present.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), 1, RoundingMode.HALF_UP);
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
