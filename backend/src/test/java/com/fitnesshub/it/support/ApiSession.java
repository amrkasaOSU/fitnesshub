package com.fitnesshub.it.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One browser's worth of state: a cookie jar and the calls that go with it.
 * Mutating requests fetch the CSRF cookie and echo it in {@code X-XSRF-TOKEN},
 * the same double-submit dance the real frontend performs, so a test that
 * passes here would pass from a browser.
 */
public class ApiSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    private final Map<String, String> extraHeaders = new LinkedHashMap<>();
    private boolean sendCsrf = true;

    public ApiSession(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // ------------------------------------------------------------ auth

    public ApiResult login(String email, String password) {
        ObjectNode body = MAPPER.createObjectNode().put("email", email).put("password", password);
        return send("POST", "/api/auth/login", body, false);
    }

    public ApiResult register(String email, String password, String firstName, String lastName,
                              String role, UUID coachId) {
        ObjectNode body = MAPPER.createObjectNode()
                .put("email", email).put("password", password)
                .put("firstName", firstName).put("lastName", lastName).put("role", role);
        if (coachId != null) {
            body.put("coachId", coachId.toString());
        }
        return send("POST", "/api/auth/register", body, false);
    }

    public ApiResult logout() {
        return send("POST", "/api/auth/logout", null, true);
    }

    /** The signed-in user's id, straight from {@code /api/users/me}. */
    public UUID userId() {
        return UUID.fromString(get("/api/users/me").data().get("id").asText());
    }

    // ------------------------------------------------------------ verbs

    public ApiResult get(String path) {
        return send("GET", path, null, false);
    }

    public ApiResult post(String path, Object body) {
        return send("POST", path, body, true);
    }

    public ApiResult patch(String path, Object body) {
        return send("PATCH", path, body, true);
    }

    public ApiResult delete(String path) {
        return send("DELETE", path, null, true);
    }

    /** Uploads one file as {@code multipart/form-data} under the field name {@code file}. */
    public ApiResult upload(String path, String filename, String contentType, byte[] bytes) {
        try {
            if (cookie("XSRF-TOKEN") == null) {
                send("GET", "/api/auth/csrf", null, false);
            }
            String boundary = "----fitnesshub-it-" + UUID.randomUUID();
            byte[] head = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] payload = new byte[head.length + bytes.length + tail.length];
            System.arraycopy(head, 0, payload, 0, head.length);
            System.arraycopy(bytes, 0, payload, head.length, bytes.length);
            System.arraycopy(tail, 0, payload, head.length + bytes.length, tail.length);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResult(response.statusCode(), MAPPER.readTree(response.body()), response.body());
        } catch (Exception e) {
            throw new IllegalStateException("upload to " + path + " failed", e);
        }
    }

    /** Status and byte length of a binary response (a progress photo, a PDF). */
    public int[] getBinary(String path) {
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new int[]{response.statusCode(), response.body().length};
        } catch (Exception e) {
            throw new IllegalStateException("GET " + path + " failed", e);
        }
    }

    /** Stops echoing the CSRF token, to prove the server rejects a forged cross-site write. */
    public ApiSession withoutCsrfHeader() {
        this.sendCsrf = false;
        return this;
    }

    public ApiSession withHeader(String name, String value) {
        extraHeaders.put(name, value);
        return this;
    }

    public String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst().orElse(null);
    }

    public static ObjectNode json() {
        return MAPPER.createObjectNode();
    }

    // --------------------------------------------------------- plumbing

    private ApiResult send(String method, String path, Object body, boolean mutating) {
        try {
            if (mutating && sendCsrf && cookie("XSRF-TOKEN") == null) {
                send("GET", "/api/auth/csrf", null, false);
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
            String payload = body == null ? null
                    : body instanceof String s ? s : MAPPER.writeValueAsString(body);
            if (payload != null) {
                request.header("Content-Type", "application/json");
            }
            if (!extraHeaders.containsKey("Accept")) {
                request.header("Accept", "application/json");
            }
            if (mutating && sendCsrf) {
                String token = cookie("XSRF-TOKEN");
                if (token != null) {
                    request.header("X-XSRF-TOKEN", token);
                }
            }
            extraHeaders.forEach(request::header);
            request.method(method, payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload));

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            JsonNode parsed = null;
            if (raw != null && !raw.isBlank()) {
                try {
                    parsed = MAPPER.readTree(raw);
                } catch (Exception notJson) {
                    // CSV exports and the like: leave body null, keep raw.
                }
            }
            return new ApiResult(response.statusCode(), parsed, raw);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }
}
