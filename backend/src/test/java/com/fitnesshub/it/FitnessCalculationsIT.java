package com.fitnesshub.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitnesshub.it.support.AbstractIntegrationTest;
import com.fitnesshub.it.support.ApiResult;
import com.fitnesshub.it.support.ApiSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer tests for the fitness maths, driven entirely through the API.
 *
 * <p>A brand-new coach builds a program, assigns it to a brand-new client, and
 * the client logs one workout plus a week of weigh-ins, steps and food. Every
 * expected value below was worked out by hand from the formulas in
 * docs/fitness-calculations.md, so a regression in the Epley estimate, volume
 * rules, PR detection, adherence, weekly weight average or the Mifflin-St Jeor
 * calorie estimate fails here with the arithmetic visible in the test.
 *
 * <p>The client is created with the default timezone (UTC), so every date the
 * test sends is computed in UTC to match what the server considers "today".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FitnessCalculationsIT extends AbstractIntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private ApiSession coach;
    private ApiSession lifter;
    private UUID lifterId;
    private String benchId;
    private String squatId;
    private String sessionId;

    @BeforeAll
    void aCoachAssignsAProgramToANewClient() {
        coach = registerCoach();
        String email = uniqueEmail("lifter");
        ApiResult created = coach.post("/api/coach/clients", ApiSession.json()
                .put("email", email).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "Lee").put("lastName", "Lifter")
                .put("fitnessGoal", "STRENGTH").put("activityLevel", "MODERATELY_ACTIVE")
                .put("sex", "MALE").put("heightCm", 180)
                .put("dateOfBirth", today.minusYears(30).minusDays(1).toString())
                .put("startingWeight", 205).put("targetWeight", 190)
                .put("dailyCalorieTarget", 2400).put("dailyProteinTarget", 160)
                .put("dailyStepTarget", 10000));
        assertThat(created.status()).isEqualTo(201);
        lifterId = UUID.fromString(created.data().get("id").asText());
        lifter = loginAs(email, DEMO_PASSWORD);

        benchId = exerciseId("Bench Press");
        squatId = exerciseId("Squat");

        // A two-day cycle: train, rest, train, rest...
        ObjectNode program = ApiSession.json()
                .put("name", "Known Answers").put("durationWeeks", 4).put("goal", "STRENGTH");
        program.putArray("days")
                .add(day(1, "Push", exercise(benchId, 0, 3, 5), exercise(squatId, 1, 3, 5)))
                .add(day(2, "Rest"));
        String programId = coach.post("/api/programs", program).data().get("id").asText();

        ApiResult assigned = coach.post("/api/programs/" + programId + "/assign", ApiSession.json()
                .put("clientId", lifterId.toString()).put("startDate", today.toString()));
        assertThat(assigned.status()).isEqualTo(201);
    }

    private String exerciseId(String name) {
        for (JsonNode e : coach.get("/api/exercises?pageSize=100").data().get("data")) {
            if (e.get("name").asText().equals(name)) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("exercise library is missing " + name);
    }

    private static ObjectNode day(int number, String name, ObjectNode... exercises) {
        ObjectNode day = ApiSession.json().put("dayNumber", number).put("name", name);
        var array = day.putArray("exercises");
        for (ObjectNode e : exercises) {
            array.add(e);
        }
        return day;
    }

    private static ObjectNode exercise(String id, int order, int sets, int reps) {
        return ApiSession.json().put("exerciseId", id).put("orderIndex", order)
                .put("sets", sets).put("targetReps", reps);
    }

    private ApiResult logSet(String exerciseId, int reps, double weight, boolean warmup, boolean failure) {
        return lifter.post("/api/workouts/" + sessionId + "/sets", ApiSession.json()
                .put("exerciseId", exerciseId).put("reps", reps).put("weight", weight)
                .put("rpe", 8).put("isWarmup", warmup).put("isFailure", failure));
    }

    private static List<String> recordTypes(ApiResult result) {
        List<String> types = new ArrayList<>();
        result.data().get("newPersonalRecords").forEach(pr -> types.add(pr.get("recordType").asText()));
        return types;
    }

    private static BigDecimal number(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }

    // ------------------------------------------------------------ schedule

    @Test
    @Order(1)
    void todayResolvesToDayOneOfTheCycle() {
        JsonNode workout = lifter.get("/api/workouts/today").data();
        sessionId = workout.get("id").asText();

        assertThat(workout.get("dayName").asText()).isEqualTo("Push");
        assertThat(workout.get("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(workout.get("exercises")).hasSize(2);
        assertThat(workout.get("exercises").get(0).get("exerciseName").asText()).isEqualTo("Bench Press");
        assertThat(workout.get("exercises").get(0).get("targetSets").asInt()).isEqualTo(3);
    }

    @Test
    @Order(2)
    void theWeekAlternatesTrainingAndRestFromTheStartDate() {
        JsonNode week = lifter.get("/api/workouts/week").data();

        assertThat(week).hasSize(7);
        for (JsonNode d : week) {
            LocalDate date = LocalDate.parse(d.get("date").asText());
            long sinceStart = java.time.temporal.ChronoUnit.DAYS.between(today, date);
            if (sinceStart < 0) {
                assertThat(d.has("programDayName")).as("%s is before the program starts", date).isFalse();
            } else if (sinceStart % 2 == 0) {
                assertThat(d.get("programDayName").asText()).as("%s", date).isEqualTo("Push");
                assertThat(d.get("restDay").asBoolean()).isFalse();
            } else {
                assertThat(d.get("programDayName").asText()).as("%s", date).isEqualTo("Rest");
                assertThat(d.get("restDay").asBoolean()).isTrue();
            }
        }
    }

    // ------------------------------------------------- sets, 1RM and PRs

    @Test
    @Order(3)
    void aWarmupSetNeverCountsAsAPersonalRecord() {
        ApiResult warmup = logSet(benchId, 10, 45, true, false);

        assertThat(warmup.status()).isEqualTo(201);
        assertThat(recordTypes(warmup)).isEmpty();
    }

    @Test
    @Order(4)
    void theFirstWorkingSetEstablishesWeightRepsAndEstimatedOneRepMax() {
        ApiResult set = logSet(benchId, 5, 100, false, false);

        assertThat(recordTypes(set)).containsExactlyInAnyOrder("MAX_WEIGHT", "MAX_REPS", "ESTIMATED_1RM");
        // Epley: 100 x (1 + 5/30) = 116.666... -> 116.67
        assertThat(prValue(set, "ESTIMATED_1RM")).isEqualByComparingTo("116.67");
        assertThat(prValue(set, "MAX_WEIGHT")).isEqualByComparingTo("100");
        assertThat(prValue(set, "MAX_REPS")).isEqualByComparingTo("5");
    }

    @Test
    @Order(5)
    void aHeavierSetForFewerRepsBeatsWeightAndOneRepMaxButNotReps() {
        ApiResult set = logSet(benchId, 3, 110, false, false);

        assertThat(recordTypes(set)).containsExactlyInAnyOrder("MAX_WEIGHT", "ESTIMATED_1RM");
        // Epley: 110 x (1 + 3/30) = 121.00
        assertThat(prValue(set, "ESTIMATED_1RM")).isEqualByComparingTo("121.00");
    }

    @Test
    @Order(6)
    void aFailedSetIsNotEvidenceOfStrengthSoItSetsNoRecord() {
        // 120 would be a weight PR and an e1RM PR if it counted.
        ApiResult failed = logSet(benchId, 2, 120, false, true);

        assertThat(failed.status()).isEqualTo(201);
        assertThat(recordTypes(failed)).isEmpty();
    }

    @Test
    @Order(7)
    void aSingleRepEstimatesAsTheWeightItself() {
        ApiResult single = logSet(squatId, 1, 225, false, false);

        assertThat(prValue(single, "ESTIMATED_1RM")).isEqualByComparingTo("225.00");
    }

    @Test
    @Order(8)
    void invalidSetsAreRejectedBeforeTheyReachTheMaths() {
        assertThat(logSet(benchId, 0, 100, false, false).status()).as("zero reps").isEqualTo(400);
        assertThat(logSet(benchId, 5, -10, false, false).status()).as("negative weight").isEqualTo(400);
        assertThat(lifter.post("/api/workouts/" + sessionId + "/sets", ApiSession.json()
                .put("exerciseId", benchId).put("reps", 5).put("weight", 100).put("rpe", 11)).status())
                .as("RPE above 10").isEqualTo(400);
    }

    // ------------------------------------------------------------ volume

    @Test
    @Order(9)
    void completingTheWorkoutTotalsVolumeExcludingWarmupsButIncludingFailedSets() {
        ApiResult completed = lifter.post("/api/workouts/" + sessionId + "/complete",
                ApiSession.json().put("overallRpe", 8).put("energyLevel", 4));

        assertThat(completed.status()).isEqualTo(200);
        JsonNode workout = completed.data();
        assertThat(workout.get("status").asText()).isEqualTo("COMPLETED");
        // bench: 100x5 + 110x3 + 120x2 (failed, still moved) = 500 + 330 + 240 = 1070
        // squat: 225x1 = 225.   warmup 45x10 excluded.   total = 1295
        assertThat(number(workout, "totalVolume")).isEqualByComparingTo("1295");
    }

    @Test
    @Order(10)
    void aCompletedWorkoutIsImmutable() {
        assertThat(logSet(benchId, 5, 100, false, false).status()).isEqualTo(409);
        assertThat(lifter.post("/api/workouts/" + sessionId + "/complete", ApiSession.json()).status())
                .isEqualTo(409);
    }

    @Test
    @Order(11)
    void exerciseProgressReportsBestsFromValidSetsOnly() {
        JsonNode progress = lifter.get("/api/exercises/" + benchId + "/progress").data();

        assertThat(number(progress, "bestWeight")).as("failed 120 ignored").isEqualByComparingTo("110");
        assertThat(progress.get("bestReps").asInt()).as("warmup's 10 reps ignored").isEqualTo(5);
        assertThat(number(progress, "bestEstimated1Rm")).isEqualByComparingTo("121.00");
        assertThat(number(progress, "latestVolume")).isEqualByComparingTo("1070");
        assertThat(progress.get("performanceTrend").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    @Order(12)
    void sessionVolumeRecordIsAwardedOnCompletion() {
        JsonNode prs = lifter.get("/api/dashboard").data().get("recentPrs");

        boolean benchVolume = false;
        for (JsonNode pr : prs) {
            if (pr.get("recordType").asText().equals("MAX_VOLUME")
                    && pr.get("exerciseName").asText().equals("Bench Press")) {
                assertThat(new BigDecimal(pr.get("value").asText())).isEqualByComparingTo("1070");
                benchVolume = true;
            }
        }
        assertThat(benchVolume).as("bench press MAX_VOLUME record present").isTrue();
    }

    // --------------------------------------------------------- adherence

    @Test
    @Order(13)
    void adherenceCountsOnlyDaysTheProgramActuallyPrescribed() {
        JsonNode summary = lifter.get("/api/analytics?from=" + today.minusDays(6) + "&to=" + today).data();

        // The window covers 7 days but the program only started today, and today is a training day.
        assertThat(summary.get("workoutsPlanned").asInt()).isEqualTo(1);
        assertThat(summary.get("workoutsCompleted").asInt()).isEqualTo(1);
        assertThat(number(summary, "adherencePercentage")).isEqualByComparingTo("100.0");
        assertThat(summary.get("totalSets").asInt()).as("4 working sets, warmup excluded").isEqualTo(4);
        assertThat(summary.get("totalReps").asInt()).as("5 + 3 + 2 + 1").isEqualTo(11);
        assertThat(number(summary, "totalVolume")).isEqualByComparingTo("1295");
    }

    @Test
    @Order(14)
    void theCoachSeesTheSameNumbersTheClientDoes() {
        JsonNode coachView = coach.get("/api/clients/" + lifterId + "/progress?from="
                + today.minusDays(6) + "&to=" + today).data();
        JsonNode clientView = lifter.get("/api/analytics?from=" + today.minusDays(6) + "&to=" + today).data();

        assertThat(coachView).isEqualTo(clientView);
        JsonNode roster = coach.get("/api/clients").data();
        assertThat(number(roster.get(0), "adherencePercentage")).isEqualByComparingTo("100.0");
    }

    @Test
    @Order(15)
    void aClientWithNoProgramHasNoAdherenceRatherThanZeroPercent() {
        String email = uniqueEmail("unassigned");
        coach.post("/api/coach/clients", ApiSession.json()
                .put("email", email).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "Una").put("lastName", "Assigned").put("fitnessGoal", "GENERAL_FITNESS"));
        ApiSession unassigned = loginAs(email, DEMO_PASSWORD);

        JsonNode dashboard = unassigned.get("/api/dashboard").data();

        assertThat(dashboard.get("weeklyAdherence").has("adherencePercentage")).isFalse();
        assertThat(dashboard.get("weeklyAdherence").get("plannedWorkouts").asInt()).isZero();
        assertThat(dashboard.get("todaysWorkout").has("programDayId")).isFalse();
    }

    // ------------------------------------------------------- body weight

    @Test
    @Order(16)
    void weeklyWeightAverageUsesTheLastCompletedMondayToSundayWeek() {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate priorMonday = thisMonday.minusWeeks(2);
        logWeight(priorMonday.plusDays(1), 201.0);
        logWeight(lastMonday, 200.0);
        logWeight(lastMonday.plusDays(2), 198.0);
        logWeight(lastMonday.plusDays(4), 196.4);

        JsonNode weight = lifter.get("/api/weight/dashboard").data();

        // (200.0 + 198.0 + 196.4) / 3 = 198.1333 -> 198.13
        assertThat(number(weight, "weeklyAverage")).isEqualByComparingTo("198.13");
        // 198.13 - 201.00 = -2.87
        assertThat(number(weight, "weeklyChange")).isEqualByComparingTo("-2.87");
        assertThat(number(weight, "current")).isEqualByComparingTo("196.4");
        assertThat(number(weight, "starting")).as("coach-entered starting weight").isEqualByComparingTo("205");
        // 196.4 - 205 = -8.60
        assertThat(number(weight, "totalChange")).isEqualByComparingTo("-8.60");
        assertThat(number(weight, "lowest")).isEqualByComparingTo("196.4");
        assertThat(number(weight, "highest")).isEqualByComparingTo("201.0");
        assertThat(number(weight, "goalWeight")).isEqualByComparingTo("190");
    }

    private void logWeight(LocalDate date, double weight) {
        ApiResult result = lifter.post("/api/weight", ApiSession.json()
                .put("weight", weight).put("recordedAt", date.atTime(12, 0).toInstant(ZoneOffset.UTC).toString()));
        assertThat(result.status()).isEqualTo(201);
    }

    // ------------------------------------------------- steps and calories

    @Test
    @Order(17)
    void stepGoalCompletionIsTodaysStepsOverTheTarget() {
        assertThat(lifter.post("/api/steps", ApiSession.json().put("steps", 8000)).status()).isEqualTo(201);

        JsonNode steps = lifter.get("/api/steps/dashboard").data();

        assertThat(steps.get("todaySteps").asInt()).isEqualTo(8000);
        assertThat(steps.get("goal").asInt()).isEqualTo(10000);
        assertThat(number(steps, "goalCompletionPercentage")).isEqualByComparingTo("80.0");
    }

    @Test
    @Order(18)
    void loggingStepsTwiceForOneDayReplacesRatherThanDoubleCounts() {
        lifter.post("/api/steps", ApiSession.json().put("steps", 8000));

        assertThat(lifter.get("/api/steps/dashboard").data().get("weeklyTotal").asInt()).isEqualTo(8000);
    }

    @Test
    @Order(19)
    void calorieEstimateFollowsMifflinStJeorPlusAStepAllowance() {
        assertThat(lifter.post("/api/nutrition", ApiSession.json()
                .put("calories", 1800).put("proteinGrams", 150)).status()).isEqualTo(201);

        JsonNode nutrition = lifter.get("/api/nutrition/dashboard").data();

        assertThat(number(nutrition, "caloriesRemaining")).as("2400 - 1800").isEqualByComparingTo("600");
        assertThat(number(nutrition, "proteinRemaining")).as("160 - 150").isEqualByComparingTo("10");

        // weight 196.4 lb x 0.453592 = 89.0854688 kg; male, 180 cm, age 30, moderately active
        // BMR  = 10 x 89.0854688 + 6.25 x 180 - 5 x 30 + 5 = 1870.854688
        // TDEE = 1870.854688 x 1.55 = 2899.8247664
        // active = 89.0854688 x 0.0005 x 8000 steps = 356.34 -> 356
        // total  = 2899.82 + 356 = 3255.82 -> 3256
        JsonNode estimate = nutrition.get("estimatedCaloriesBurned");
        assertThat(estimate.get("available").asBoolean()).isTrue();
        assertThat(number(estimate, "estimatedActiveCalories")).isEqualByComparingTo("356");
        assertThat(number(estimate, "estimatedTotalCalories")).isEqualByComparingTo("3256");
        // 1800 eaten - 3256 burned = -1456
        assertThat(number(nutrition, "estimatedCalorieBalance")).isEqualByComparingTo("-1456");
    }

    @Test
    @Order(20)
    void calorieEstimateIsUnavailableRatherThanGuessedWhenTheProfileIsIncomplete() {
        // Seeded client 5 has a profile but this fresh client has no height, sex or birth date.
        String email = uniqueEmail("sparse");
        coach.post("/api/coach/clients", ApiSession.json()
                .put("email", email).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "Sparse").put("lastName", "Profile").put("fitnessGoal", "FAT_LOSS"));

        JsonNode nutrition = loginAs(email, DEMO_PASSWORD).get("/api/nutrition/dashboard").data();

        assertThat(nutrition.get("estimatedCaloriesBurned").get("available").asBoolean()).isFalse();
        assertThat(nutrition.has("estimatedCalorieBalance")).isFalse();
        assertThat(nutrition.has("calorieTarget")).as("no default target is invented").isFalse();
    }

    private static BigDecimal prValue(ApiResult result, String type) {
        for (JsonNode pr : result.data().get("newPersonalRecords")) {
            if (pr.get("recordType").asText().equals(type)) {
                return new BigDecimal(pr.get("value").asText());
            }
        }
        throw new AssertionError("no " + type + " record in " + result);
    }
}
