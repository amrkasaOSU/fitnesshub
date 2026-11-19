package com.fitnesshub.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitnesshub.it.support.AbstractIntegrationTest;
import com.fitnesshub.it.support.ApiResult;
import com.fitnesshub.it.support.ApiSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tests that matter most in a multi-tenant app: proof that one person's
 * data cannot be read or changed by someone who doesn't own it.
 *
 * <p>Cast: <b>Alex</b> (seeded client 1) is the victim. <b>Sam</b> (seeded
 * client 2) is another client of the same coach. <b>Jordan</b> is their coach.
 * <b>The rival</b> is a second, unrelated coach with a client of their own.
 * Every attack is attempted over real HTTP with a real session, against the
 * real database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnershipIT extends AbstractIntegrationTest {

    // A valid 1x1 PNG, so the upload passes content-type checks.
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private ApiSession alex;
    private ApiSession sam;
    private ApiSession jordan;
    private ApiSession rival;
    private ApiSession rivalsClient;

    private UUID alexId;
    private UUID samId;
    private UUID rivalsClientId;
    private String alexSessionId;
    private String alexCheckInId;
    private String alexPhotoId;
    private String alexMessageId;
    private String jordansProgramId;
    private String rivalsProgramId;
    private String benchPressId;

    @BeforeAll
    void cast() {
        alex = demoClient(1);
        sam = demoClient(2);
        jordan = demoCoach();
        rival = registerCoach();
        alexId = alex.userId();
        samId = sam.userId();

        String rivalsClientEmail = uniqueEmail("rivals-client");
        ApiResult created = rival.post("/api/coach/clients", ApiSession.json()
                .put("email", rivalsClientEmail).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "Robin").put("lastName", "Rival").put("fitnessGoal", "STRENGTH"));
        assertThat(created.status()).isEqualTo(201);
        rivalsClientId = UUID.fromString(created.data().get("id").asText());
        rivalsClient = loginAs(rivalsClientEmail, DEMO_PASSWORD);

        alexSessionId = alex.get("/api/workouts?pageSize=50").data().get("data").get(0).get("id").asText();
        alexCheckInId = alex.get("/api/checkins").data().get("data").get(0).get("id").asText();
        alexPhotoId = alex.upload("/api/progress-photos", "front.png", "image/png", TINY_PNG)
                .data().get("id").asText();
        alexMessageId = jordan.post("/api/messages", ApiSession.json()
                .put("clientId", alexId.toString()).put("content", "Private note for Alex only."))
                .data().get("id").asText();

        jordansProgramId = jordan.get("/api/programs").data().get("data").get(0).get("id").asText();
        benchPressId = findExercise("Bench Press");
        rivalsProgramId = rival.post("/api/programs", ApiSession.json()
                .put("name", "Rival Program").put("durationWeeks", 4).put("goal", "STRENGTH")
                .set("days", ApiSession.json().arrayNode().add(ApiSession.json()
                        .put("dayNumber", 1).put("name", "Day 1")
                        .set("exercises", ApiSession.json().arrayNode().add(ApiSession.json()
                                .put("exerciseId", benchPressId).put("orderIndex", 0)
                                .put("sets", 3).put("targetReps", 5))))))
                .data().get("id").asText();
    }

    private String findExercise(String name) {
        for (JsonNode e : alex.get("/api/exercises?pageSize=100").data().get("data")) {
            if (e.get("name").asText().equals(name)) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("seeded exercise missing: " + name);
    }

    // ------------------------------------------------ records addressed by client id in the path

    @ParameterizedTest(name = "GET /api/clients/'{'alex'}'{0}")
    @ValueSource(strings = {"", "/progress", "/workouts", "/nutrition", "/checkins"})
    void aClientsRecordsAreOnlyVisibleToThemAndTheirOwnCoach(String suffix) {
        String path = "/api/clients/" + alexId + suffix;

        assertThat(anonymous().get(path).status()).as("signed-out visitor").isEqualTo(401);
        assertThat(sam.get(path).status()).as("another client of the same coach").isEqualTo(403);
        assertThat(rival.get(path).status()).as("an unrelated coach").isEqualTo(403);
        assertThat(rivalsClient.get(path).status()).as("an unrelated coach's client").isEqualTo(403);

        assertThat(alex.get(path).status()).as("the client themselves").isEqualTo(200);
        assertThat(jordan.get(path).status()).as("their own coach").isEqualTo(200);
    }

    @Test
    void theCoachRosterNeverIncludesAnotherCoachsClients() {
        JsonNode rivalsRoster = rival.get("/api/clients").data();
        JsonNode jordansRoster = jordan.get("/api/clients").data();

        assertThat(rivalsRoster).hasSize(1);
        assertThat(rivalsRoster.get(0).get("clientId").asText()).isEqualTo(rivalsClientId.toString());
        assertThat(jordansRoster.toString()).doesNotContain(rivalsClientId.toString());
        assertThat(rival.get("/api/coach/dashboard").data().get("totalClients").asInt()).isEqualTo(1);
    }

    @Test
    void thePdfProgressReportIsNotDownloadableByOutsiders() {
        String path = "/api/clients/" + alexId + "/report.pdf";

        assertThat(sam.getBinary(path)[0]).isEqualTo(403);
        assertThat(rival.getBinary(path)[0]).isEqualTo(403);
        assertThat(jordan.getBinary(path)[0]).isEqualTo(200);
    }

    // ------------------------------------------------ records addressed by ?clientId=

    @ParameterizedTest(name = "GET {0}?clientId='{'alex'}'")
    @ValueSource(strings = {
            "/api/weight", "/api/weight/dashboard", "/api/steps", "/api/steps/dashboard",
            "/api/nutrition", "/api/nutrition/dashboard", "/api/goals", "/api/workouts",
            "/api/checkins", "/api/analytics", "/api/progress-photos", "/api/messages"})
    void aClientIdQueryParameterCannotBeUsedToReadSomeoneElsesData(String endpoint) {
        String spoofed = endpoint + "?clientId=" + alexId;

        // An unrelated coach naming a client they don't coach is refused outright.
        assertThat(rival.get(spoofed).status()).as("unrelated coach").isEqualTo(403);

        // A client's own id is always used, whatever id they send: Sam asking
        // for Alex's data gets exactly what Sam gets when asking for his own.
        ApiResult samSpoofing = sam.get(spoofed);
        ApiResult samHonest = sam.get(endpoint);
        assertThat(samSpoofing.status()).isEqualTo(200);
        assertThat(samSpoofing.raw()).as("spoofed id is ignored for a client").isEqualTo(samHonest.raw());

        // And the coach who does own Alex can read it.
        assertThat(jordan.get(spoofed).status()).as("owning coach").isEqualTo(200);
    }

    @Test
    void whatSamSeesWhenSpoofingReallyIsHisOwnDataNotAlexs() {
        // Guards the test above against passing vacuously if both responses were empty.
        String alexWeights = alex.get("/api/weight?pageSize=100").raw();
        String samSpoofing = sam.get("/api/weight?pageSize=100&clientId=" + alexId).raw();

        assertThat(alexWeights).contains("\"totalItems\":");
        assertThat(samSpoofing).isNotEqualTo(alexWeights);
    }

    @Test
    void exerciseProgressCannotBeReadAcrossTenants() {
        String path = "/api/exercises/" + benchPressId + "/progress?clientId=" + alexId;

        assertThat(rival.get(path).status()).isEqualTo(403);
        assertThat(sam.get(path).raw())
                .isEqualTo(sam.get("/api/exercises/" + benchPressId + "/progress").raw());
        assertThat(jordan.get(path).data().get("bestWeight").decimalValue()).isPositive();
    }

    // ------------------------------------------------ workout sessions

    @Test
    void aWorkoutSessionIsReadableOnlyByItsOwnerAndTheirCoach() {
        String path = "/api/workouts/" + alexSessionId;

        assertThat(sam.get(path).status()).isEqualTo(403);
        assertThat(rival.get(path).status()).isEqualTo(403);
        assertThat(rivalsClient.get(path).status()).isEqualTo(403);
        assertThat(alex.get(path).status()).isEqualTo(200);
        assertThat(jordan.get(path).status()).isEqualTo(200);
    }

    @Test
    void nobodyButTheOwnerCanLogASetIntoAWorkout() {
        String path = "/api/workouts/" + alexSessionId + "/sets";
        var set = ApiSession.json().put("exerciseId", benchPressId).put("reps", 5).put("weight", 999);

        assertThat(sam.post(path, set).status()).as("another client").isEqualTo(403);
        assertThat(rival.post(path, set).status()).as("unrelated coach").isEqualTo(403);
        // Even Alex's own coach reads the log but does not write into it.
        assertThat(jordan.post(path, set).status()).as("owning coach").isEqualTo(403);
        // Check the logged weights themselves. Searching the raw JSON for "999"
        // was flaky: the response is full of random UUIDs and microsecond
        // timestamps, and one run in CI matched "999" inside one of those.
        List<Double> weights = new java.util.ArrayList<>();
        for (JsonNode exercise : alex.get("/api/workouts/" + alexSessionId).data().get("exercises")) {
            for (JsonNode logged : exercise.get("completedSets")) {
                weights.add(logged.get("weight").asDouble());
            }
        }
        assertThat(weights).doesNotContain(999.0);
    }

    @Test
    void onlyTheOwningCoachCanLeaveWorkoutFeedback() {
        String path = "/api/workouts/" + alexSessionId + "/feedback";
        var feedback = ApiSession.json().put("content", "Nice work.");

        assertThat(rival.post(path, feedback).status()).isEqualTo(403);
        assertThat(sam.post(path, feedback).status()).isEqualTo(403);
        assertThat(jordan.post(path, feedback).status()).isEqualTo(201);
    }

    // ------------------------------------------------ coach-only writes

    @Test
    void aCoachCannotResetThePasswordOfSomeoneElsesClient() {
        ApiResult attempt = rival.post("/api/coach/clients/" + alexId + "/password",
                ApiSession.json().put("temporaryPassword", "Hijacked!2025"));

        assertThat(attempt.status()).isEqualTo(403);
        assertThat(anonymous().login("client1@fitnesshub.local", "Hijacked!2025").status()).isEqualTo(401);
        assertThat(anonymous().login("client1@fitnesshub.local", DEMO_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void coachNotesArePrivateToTheCoachWhoWroteThem() {
        String path = "/api/coach/clients/" + alexId + "/notes";
        assertThat(jordan.post(path, ApiSession.json().put("content", "Confidential: struggling with sleep."))
                .status()).isEqualTo(201);

        assertThat(rival.get(path).status()).as("unrelated coach").isEqualTo(403);
        assertThat(rival.post(path, ApiSession.json().put("content", "x")).status()).isEqualTo(403);
        // The client the notes are about cannot read them either.
        assertThat(alex.get(path).status()).as("the client themselves").isEqualTo(403);
        assertThat(jordan.get(path).raw()).contains("struggling with sleep");
    }

    @Test
    void aCoachCannotEditTargetsForSomeoneElsesClient() {
        ApiResult attempt = rival.patch("/api/clients/" + alexId,
                ApiSession.json().put("dailyCalorieTarget", 500));

        assertThat(attempt.status()).isEqualTo(403);
        assertThat(jordan.get("/api/clients/" + alexId).data().get("dailyCalorieTarget").asInt()).isEqualTo(2400);
    }

    @Test
    void programsAreScopedToTheCoachWhoBuiltThem() {
        assertThat(rival.get("/api/programs/" + jordansProgramId).status()).isEqualTo(403);
        assertThat(rivalsClient.get("/api/programs/" + jordansProgramId).status()).isEqualTo(403);
        assertThat(rival.get("/api/programs").raw()).doesNotContain(jordansProgramId);
        // Alex is assigned one of Jordan's programs, so Alex may read it.
        assertThat(jordan.get("/api/programs/" + jordansProgramId).status()).isEqualTo(200);
    }

    @Test
    void aCoachCannotAssignAProgramToSomeoneElsesClient() {
        var assignment = ApiSession.json().put("clientId", alexId.toString())
                .put("startDate", LocalDate.now().toString());

        assertThat(rival.post("/api/programs/" + rivalsProgramId + "/assign", assignment).status())
                .as("rival's own program, someone else's client").isEqualTo(403);
        assertThat(rival.post("/api/programs/" + jordansProgramId + "/assign", assignment).status())
                .as("someone else's program entirely").isEqualTo(403);
    }

    @Test
    void aCoachCannotAnswerSomeoneElsesClientsCheckIn() {
        ApiResult attempt = rival.patch("/api/checkins/" + alexCheckInId + "/review",
                ApiSession.json().put("coachResponse", "I am not your coach."));

        assertThat(attempt.status()).isEqualTo(403);
        assertThat(alex.get("/api/checkins").raw()).doesNotContain("I am not your coach.");
    }

    @Test
    void aiClientAnalysisIsRefusedForSomeoneElsesClient() {
        ApiResult attempt = rival.post("/api/ai/client-analysis",
                ApiSession.json().put("clientId", alexId.toString()));

        assertThat(attempt.status()).isEqualTo(403);
    }

    // ------------------------------------------------ messages

    @Test
    void aMessageCanOnlyBeMarkedReadByAParticipant() {
        String path = "/api/messages/" + alexMessageId + "/read";

        assertThat(sam.patch(path, null).status()).isEqualTo(403);
        assertThat(rival.patch(path, null).status()).isEqualTo(403);
        assertThat(alex.patch(path, null).status()).isEqualTo(200);
    }

    @Test
    void aCoachCannotMessageOrReadTheThreadOfSomeoneElsesClient() {
        assertThat(rival.post("/api/messages", ApiSession.json()
                .put("clientId", alexId.toString()).put("content", "Switch to me instead")).status()).isEqualTo(403);
        assertThat(rival.get("/api/coach/conversations").raw()).doesNotContain("Private note for Alex only.");
        assertThat(sam.get("/api/messages").raw()).doesNotContain("Private note for Alex only.");
        assertThat(alex.get("/api/messages").raw()).contains("Private note for Alex only.");
    }

    // ------------------------------------------------ progress photos

    @Test
    void progressPhotoBytesAreServedOnlyToTheOwnerAndTheirCoach() {
        String path = "/api/progress-photos/" + alexPhotoId + "/image";

        assertThat(anonymous().getBinary(path)[0]).as("signed-out visitor").isEqualTo(401);
        assertThat(sam.getBinary(path)[0]).as("another client").isEqualTo(403);
        assertThat(rival.getBinary(path)[0]).as("unrelated coach").isEqualTo(403);
        assertThat(rivalsClient.getBinary(path)[0]).as("unrelated client").isEqualTo(403);

        int[] owner = alex.getBinary(path);
        assertThat(owner[0]).isEqualTo(200);
        assertThat(owner[1]).isEqualTo(TINY_PNG.length);
        assertThat(jordan.getBinary(path)[0]).as("owning coach may view").isEqualTo(200);
    }

    @Test
    void aCoachCanViewButNeverUploadOrDeleteAClientsPhotos() {
        assertThat(jordan.upload("/api/progress-photos", "x.png", "image/png", TINY_PNG).status())
                .as("coach upload").isEqualTo(403);
        assertThat(jordan.delete("/api/progress-photos/" + alexPhotoId).status())
                .as("coach delete").isIn(403, 409);
        assertThat(sam.delete("/api/progress-photos/" + alexPhotoId).status())
                .as("another client delete").isIn(403, 409);
        assertThat(alex.getBinary("/api/progress-photos/" + alexPhotoId + "/image")[0])
                .as("photo survived").isEqualTo(200);
    }

    // ------------------------------------------------ notifications

    @Test
    void notificationsCannotBeMarkedReadByAnotherUser() {
        JsonNode alexNotifications = alex.get("/api/notifications").data().get("data");
        assertThat(alexNotifications).isNotEmpty();
        String id = alexNotifications.get(0).get("id").asText();

        assertThat(sam.patch("/api/notifications/" + id + "/read", null).status()).isEqualTo(403);
        assertThat(alex.patch("/api/notifications/" + id + "/read", null).status()).isEqualTo(200);
    }

    @Test
    void unknownIdsAreNotFoundRatherThanLeakingExistence() {
        UUID nobody = UUID.randomUUID();

        assertThat(jordan.get("/api/workouts/" + nobody).status()).isEqualTo(404);
        assertThat(jordan.get("/api/clients/" + nobody).status()).isEqualTo(403);
    }
}
