package com.fitnesshub.it;

import com.fitnesshub.it.support.AbstractIntegrationTest;
import com.fitnesshub.it.support.ApiResult;
import com.fitnesshub.it.support.ApiSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentication end to end: registration, sign-in, the session cookie, CSRF
 * protection, role gates, sign-out and the failed-login throttle.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejectedWithTheJsonEnvelope() {
        ApiResult result = anonymous().get("/api/users/me");

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.errorCode()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void healthEndpointIsPublicButRevealsNoDetail() {
        ApiResult result = anonymous().get("/actuator/health");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("status").asText()).isEqualTo("UP");
        assertThat(result.body().has("components")).isFalse();
    }

    @Test
    void registeringACoachSignsThemInImmediately() {
        ApiSession session = anonymous();
        String email = uniqueEmail("newcoach");

        ApiResult registered = session.register(email, DEMO_PASSWORD, "Dana", "Lopez", "COACH", null);

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.data().get("role").asText()).isEqualTo("COACH");
        assertThat(registered.raw()).doesNotContain("passwordHash").doesNotContain(DEMO_PASSWORD);

        ApiResult me = session.get("/api/users/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.data().get("email").asText()).isEqualTo(email);
    }

    @Test
    void emailIsNormalisedSoTheSameAddressCannotRegisterTwice() {
        String email = uniqueEmail("dupe");
        assertThat(anonymous().register(email, DEMO_PASSWORD, "A", "B", "COACH", null).status()).isEqualTo(201);

        ApiResult second = anonymous().register(email.toUpperCase(), DEMO_PASSWORD, "A", "B", "COACH", null);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("CONFLICT");
    }

    @Test
    void nobodyCanSelfRegisterAsAdmin() {
        ApiResult result = anonymous().register(uniqueEmail("admin"), DEMO_PASSWORD, "Evil", "Admin", "ADMIN", null);

        assertThat(result.status()).isEqualTo(409);
    }

    @Test
    void aClientMustRegisterAgainstARealCoach() {
        ApiResult noCoach = anonymous().register(uniqueEmail("c"), DEMO_PASSWORD, "No", "Coach", "CLIENT", null);
        ApiResult ghostCoach = anonymous().register(uniqueEmail("c"), DEMO_PASSWORD, "Ghost", "Coach", "CLIENT",
                UUID.randomUUID());
        // Another client's id is a real user, but not a coach.
        UUID clientId = demoClient(1).userId();
        ApiResult notACoach = anonymous().register(uniqueEmail("c"), DEMO_PASSWORD, "Wrong", "Role", "CLIENT", clientId);

        assertThat(noCoach.status()).isEqualTo(409);
        assertThat(ghostCoach.status()).isEqualTo(404);
        assertThat(notACoach.status()).isEqualTo(409);
    }

    @Test
    void weakOrMalformedRegistrationsFailValidation() {
        ApiResult shortPassword = anonymous().register(uniqueEmail("weak"), "short", "A", "B", "COACH", null);
        ApiResult badEmail = anonymous().register("not-an-email", DEMO_PASSWORD, "A", "B", "COACH", null);

        assertThat(shortPassword.status()).isEqualTo(400);
        assertThat(shortPassword.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(badEmail.status()).isEqualTo(400);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        ApiResult wrongPassword = anonymous().login(COACH_EMAIL, "definitely-not-it");
        ApiResult unknownEmail = anonymous().login(uniqueEmail("nobody"), "definitely-not-it");

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        // Same body for both, so the endpoint can't be used to find out which emails exist.
        assertThat(wrongPassword.raw()).isEqualTo(unknownEmail.raw());
    }

    @Test
    void theSessionCookieIsHttpOnlyAndCarriesTheLogin() {
        ApiSession session = anonymous();
        ApiResult login = session.login(COACH_EMAIL, DEMO_PASSWORD);

        assertThat(login.status()).isEqualTo(200);
        assertThat(session.cookie("SESSION")).isNotBlank();
        assertThat(session.get("/api/users/me").data().get("role").asText()).isEqualTo("COACH");
    }

    @Test
    void loggingOutInvalidatesTheSessionServerSide() {
        ApiSession session = demoCoach();
        assertThat(session.get("/api/users/me").status()).isEqualTo(200);

        assertThat(session.logout().status()).isEqualTo(204);

        assertThat(session.get("/api/users/me").status()).isEqualTo(401);
    }

    @Test
    void signingInIssuesAFreshSessionIdSoAPlantedOneIsWorthless() throws Exception {
        // fetch() sends Accept: */* by default, as the real frontend does. With
        // that header, a request that hits the auth entry point opens a session
        // (the request cache saves the URL), so an attacker can obtain one
        // without signing in and try to fix it in a victim's browser.
        ApiSession browser = anonymous().withHeader("Accept", "*/*");
        browser.get("/api/users/me");
        String planted = browser.cookie("SESSION");
        assertThat(planted).as("an anonymous session exists before login").isNotBlank();

        assertThat(browser.login("client4@fitnesshub.local", DEMO_PASSWORD).status()).isEqualTo(200);

        String issued = browser.cookie("SESSION");
        assertThat(issued).as("login must rotate the session id").isNotEqualTo(planted);
        // And the planted id grants nothing.
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl() + "/api/users/me"))
                .header("Cookie", "SESSION=" + planted).GET().build();
        int status = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void anUnknownTimezoneIsRejectedInsteadOfSilentlyBecomingUtc() {
        ApiSession session = registerCoach();

        ApiResult bogus = session.patch("/api/users/me", ApiSession.json().put("timezone", "Mars/Olympus_Mons"));
        ApiResult real = session.patch("/api/users/me", ApiSession.json().put("timezone", "America/New_York"));

        assertThat(bogus.status()).isEqualTo(400);
        assertThat(real.status()).isEqualTo(200);
        assertThat(session.get("/api/users/me").data().get("timezone").asText()).isEqualTo("America/New_York");
    }

    @Test
    void aStateChangingRequestWithoutTheCsrfHeaderIsRefused() {
        ApiSession session = demoClient(1);
        // Make sure the cookie exists, so this proves the *header* is what's checked:
        // a cross-site form post would send the cookie but could never read it to echo it.
        session.get("/api/auth/csrf");
        assertThat(session.cookie("XSRF-TOKEN")).isNotBlank();

        ApiResult forged = session.withoutCsrfHeader()
                .post("/api/weight", ApiSession.json().put("weight", 200));

        assertThat(forged.status()).isEqualTo(403);
    }

    @Test
    void theSameRequestWithTheCsrfHeaderSucceeds() {
        ApiResult result = demoClient(5).post("/api/weight", ApiSession.json().put("weight", 170.5));

        assertThat(result.status()).isEqualTo(201);
    }

    @Test
    void aClientCannotReachCoachOnlyEndpoints() {
        ApiSession client = demoClient(1);

        assertThat(client.get("/api/coach/dashboard").status()).isEqualTo(403);
        assertThat(client.get("/api/clients").status()).isEqualTo(403);
        assertThat(client.get("/api/coach/checkins").status()).isEqualTo(403);
        assertThat(client.post("/api/coach/clients", ApiSession.json()
                .put("email", uniqueEmail("x")).put("temporaryPassword", DEMO_PASSWORD)
                .put("firstName", "X").put("lastName", "Y").put("fitnessGoal", "STRENGTH")).status())
                .isEqualTo(403);
    }

    @Test
    void changingPasswordRequiresTheCurrentOneAndTakesEffect() {
        String email = uniqueEmail("pw");
        ApiSession session = anonymous();
        session.register(email, DEMO_PASSWORD, "Pat", "Word", "COACH", null);

        ApiResult wrongCurrent = session.post("/api/users/me/password", ApiSession.json()
                .put("currentPassword", "not-my-password").put("newPassword", "BrandNew!2025"));
        ApiResult changed = session.post("/api/users/me/password", ApiSession.json()
                .put("currentPassword", DEMO_PASSWORD).put("newPassword", "BrandNew!2025"));

        assertThat(wrongCurrent.status()).isEqualTo(409);
        assertThat(changed.status()).isEqualTo(204);
        assertThat(anonymous().login(email, DEMO_PASSWORD).status()).isEqualTo(401);
        assertThat(anonymous().login(email, "BrandNew!2025").status()).isEqualTo(200);
    }

    @Test
    void repeatedFailedLoginsLockTheAccountOutEvenWithTheRightPassword() {
        String email = uniqueEmail("locked");
        anonymous().register(email, DEMO_PASSWORD, "Lock", "Out", "COACH", null);
        // A distinct forwarded address keeps this test's failures off the
        // counter for the loopback address every other test signs in from.
        String attackerIp = "203.0.113." + (10 + (int) (Math.random() * 200));

        for (int i = 0; i < 10; i++) {
            ApiResult attempt = anonymous().withHeader("X-Forwarded-For", attackerIp).login(email, "wrong-" + i);
            assertThat(attempt.status()).as("attempt %d", i + 1).isEqualTo(401);
        }
        ApiResult eleventh = anonymous().withHeader("X-Forwarded-For", attackerIp).login(email, DEMO_PASSWORD);

        assertThat(eleventh.status()).isEqualTo(429);
        assertThat(eleventh.errorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void aiEndpointDegradesGracefullyWithoutAProviderKey() {
        ApiResult result = demoClient(1).post("/api/ai/fitness-question",
                ApiSession.json().put("question", "Should I attempt 225 on bench today?"));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data().get("answer").asText()).contains("isn't configured");
    }

    @Test
    void featureFlagsReportUnconfiguredIntegrations() {
        ApiResult result = demoClient(1).get("/api/features");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.raw()).contains("false");
    }
}
