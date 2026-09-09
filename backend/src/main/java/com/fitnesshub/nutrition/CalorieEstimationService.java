package com.fitnesshub.nutrition;

import com.fitnesshub.bodyweight.WeightEntryRepository;
import com.fitnesshub.client.ActivityLevel;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.client.Sex;
import com.fitnesshub.nutrition.dto.CalorieEstimate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Every number here is explicitly an estimate (Mifflin-St Jeor BMR + a flat
 * kcal-per-step walking estimate), never presented as measured. See
 * docs/fitness-calculations.md for the exact formulas and their sources.
 */
@Service
public class CalorieEstimationService {

    private final ClientProfileRepository clientProfileRepository;
    private final WeightEntryRepository weightEntryRepository;

    public CalorieEstimationService(ClientProfileRepository clientProfileRepository,
                                     WeightEntryRepository weightEntryRepository) {
        this.clientProfileRepository = clientProfileRepository;
        this.weightEntryRepository = weightEntryRepository;
    }

    @Transactional(readOnly = true)
    public CalorieEstimate estimateForToday(UUID clientId, int todaySteps) {
        ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);
        if (profile == null || profile.getHeightCm() == null || profile.getDateOfBirth() == null || profile.getSex() == null) {
            return CalorieEstimate.unavailable("Add height, date of birth, and sex to your profile to see estimated calories burned.");
        }
        var latestWeight = weightEntryRepository.findByClientIdOrderByRecordedAtAsc(clientId);
        if (latestWeight.isEmpty()) {
            return CalorieEstimate.unavailable("Log a body weight entry to see estimated calories burned.");
        }
        BigDecimal weightLb = latestWeight.get(latestWeight.size() - 1).getWeight();
        BigDecimal weightKg = weightLb.multiply(new BigDecimal("0.453592"));

        int age = Period.between(profile.getDateOfBirth(), LocalDate.now()).getYears();
        BigDecimal bmr = mifflinStJeor(weightKg, profile.getHeightCm(), age, profile.getSex());
        BigDecimal tdee = bmr.multiply(activityMultiplier(profile.getActivityLevel()));

        BigDecimal kcalPerStep = weightKg.multiply(new BigDecimal("0.0005"));
        BigDecimal activeCalories = kcalPerStep.multiply(BigDecimal.valueOf(todaySteps))
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal totalCalories = tdee.add(activeCalories).setScale(0, RoundingMode.HALF_UP);

        return new CalorieEstimate(true, activeCalories, totalCalories,
                "Estimated using the Mifflin-St Jeor formula and a step-based activity estimate.");
    }

    private BigDecimal mifflinStJeor(BigDecimal weightKg, BigDecimal heightCm, int age, Sex sex) {
        BigDecimal base = BigDecimal.valueOf(10).multiply(weightKg)
                .add(new BigDecimal("6.25").multiply(heightCm))
                .subtract(BigDecimal.valueOf(5L * age));
        BigDecimal adjustment = switch (sex) {
            case MALE -> BigDecimal.valueOf(5);
            case FEMALE -> BigDecimal.valueOf(-161);
            case OTHER -> BigDecimal.valueOf(-78); // midpoint approximation
        };
        return base.add(adjustment);
    }

    private BigDecimal activityMultiplier(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> new BigDecimal("1.2");
            case LIGHTLY_ACTIVE -> new BigDecimal("1.375");
            case MODERATELY_ACTIVE -> new BigDecimal("1.55");
            case VERY_ACTIVE -> new BigDecimal("1.725");
            case EXTREMELY_ACTIVE -> new BigDecimal("1.9");
        };
    }
}
