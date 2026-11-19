package com.fitnesshub.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitnesshub.it.support.AbstractIntegrationTest;
import com.fitnesshub.it.support.ApiResult;
import com.fitnesshub.it.support.ApiSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Today's workout from "opened the app" to "finished", and what the coach
 * sees at each stage. Each test gets its own coach and client, because the
 * point is the exact count on that coach's dashboard.
 */
class WorkoutLifecycleIT extends AbstractIntegrationTest {

    private record Pair(ApiSession coach, ApiSession client, UUID clientId, String exerciseId) { }

    private Pair coachWithOneClientOnAProgram() {
        ApiSession coach = registerCoach();
        String email = uniqueEmail("lifecycle");
        UUID clientId = UUID.fromString(coach.post("/api/coach/clients", ApiSession.json()
                .put("email", email).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "Life").put("lastName", "Cycle").put("fitnessGoal", "STRENGTH"))
                .data().get("id").asText());

        String bench = null;
        for (JsonNode e : coach.get("/api/exercises?pageSize=100").data().get("data")) {
            if (e.get("name").asText().equals("Bench Press")) {
                bench = e.get("id").asText();
            }
        }
        var program = ApiSession.json().put("name", "Daily").put("durationWeeks", 4).put("goal", "STRENGTH");
        program.putArray("days").add(ApiSession.json().put("dayNumber", 1).put("name", "Every Day")
                .set("exercises", ApiSession.json().arrayNode().add(ApiSession.json()
                        .put("exerciseId", bench).put("orderIndex", 0).put("sets", 3).put("targetReps", 5))));
        String programId = coach.post("/api/programs", program).data().get("id").asText();
        coach.post("/api/programs/" + programId + "/assign", ApiSession.json()
                .put("clientId", clientId.toString()).put("startDate", LocalDate.now(ZoneOffset.UTC).toString()));

        return new Pair(coach, loginAs(email, DEMO_PASSWORD), clientId, bench);
    }

    private int trainingToday(ApiSession coach) {
        return coach.get("/api/coach/dashboard").data().get("clientsTrainingToday").asInt();
    }

    @Test
    void theDashboardsWorkoutIsARealSessionTheClientCanOpen() {
        Pair p = coachWithOneClientOnAProgram();

        String fromDashboard = p.client().get("/api/dashboard").data().get("todaysWorkout").get("id").asText();
        ApiResult opened = p.client().get("/api/workouts/" + fromDashboard);

        assertThat(opened.status()).as("the id the dashboard handed out must exist").isEqualTo(200);
        assertThat(p.client().get("/api/workouts/today").data().get("id").asText())
                .as("dashboard and Today page are the same session").isEqualTo(fromDashboard);
    }

    @Test
    void merelyOpeningTheAppIsNotTraining() {
        Pair p = coachWithOneClientOnAProgram();

        JsonNode today = p.client().get("/api/workouts/today").data();
        p.client().get("/api/dashboard");

        assertThat(today.get("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(trainingToday(p.coach())).as("viewing the plan is not a workout").isZero();
    }

    @Test
    void loggingTheFirstSetStartsTheWorkoutAndTheCoachSeesIt() {
        Pair p = coachWithOneClientOnAProgram();
        String sessionId = p.client().get("/api/workouts/today").data().get("id").asText();

        ApiResult set = p.client().post("/api/workouts/" + sessionId + "/sets", ApiSession.json()
                .put("exerciseId", p.exerciseId()).put("reps", 5).put("weight", 135));

        assertThat(set.status()).isEqualTo(201);
        assertThat(p.client().get("/api/workouts/today").data().get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(trainingToday(p.coach())).isEqualTo(1);
    }

    @Test
    void skippingTellsTheCoachWhyAndIsNotCountedAsTraining() {
        Pair p = coachWithOneClientOnAProgram();
        p.client().get("/api/workouts/today");

        ApiResult skipped = p.client().post("/api/workouts/today/skip",
                ApiSession.json().put("reason", "Sick with a cold"));

        assertThat(skipped.status()).isEqualTo(200);
        assertThat(skipped.data().get("status").asText()).isEqualTo("SKIPPED");
        assertThat(p.coach().get("/api/notifications").raw()).contains("Sick with a cold");
        assertThat(p.client().post("/api/workouts/today/skip", ApiSession.json().put("reason", "again")).status())
                .as("can't skip twice").isEqualTo(409);
        // A skip is a status other than NOT_STARTED, and the dashboard's rule is
        // "anything but not-started", so a skip counts. Pinned here so a change
        // to that rule is a decision, not an accident.
        assertThat(trainingToday(p.coach())).isEqualTo(1);
    }

    @Test
    void historyListsTheSessionWithItsVolumeOnceFinished() {
        Pair p = coachWithOneClientOnAProgram();
        String sessionId = p.client().get("/api/workouts/today").data().get("id").asText();
        p.client().post("/api/workouts/" + sessionId + "/sets", ApiSession.json()
                .put("exerciseId", p.exerciseId()).put("reps", 5).put("weight", 100));
        p.client().post("/api/workouts/" + sessionId + "/sets", ApiSession.json()
                .put("exerciseId", p.exerciseId()).put("reps", 5).put("weight", 110));
        p.client().post("/api/workouts/" + sessionId + "/complete", ApiSession.json());

        JsonNode row = p.coach().get("/api/clients/" + p.clientId() + "/workouts").data().get("data").get(0);

        assertThat(row.get("id").asText()).isEqualTo(sessionId);
        assertThat(row.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(row.get("dayName").asText()).isEqualTo("Every Day");
        assertThat(row.get("totalVolume").decimalValue()).isEqualByComparingTo("1050"); // 500 + 550
        assertThat(row.get("prCount").asInt()).as("first-ever sets set records").isPositive();
    }
}
