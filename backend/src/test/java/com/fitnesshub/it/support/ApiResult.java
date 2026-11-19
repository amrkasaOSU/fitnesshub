package com.fitnesshub.it.support;

import com.fasterxml.jackson.databind.JsonNode;

/** Status code plus the parsed JSON body (or the raw text when it wasn't JSON). */
public record ApiResult(int status, JsonNode body, String raw) {

    /** The {@code data} field of the success envelope. */
    public JsonNode data() {
        return body == null ? null : body.get("data");
    }

    /** The {@code error.code} field of the error envelope, or null on success. */
    public String errorCode() {
        return body == null || body.get("error") == null ? null : body.get("error").path("code").asText(null);
    }

    @Override
    public String toString() {
        return "HTTP " + status + " " + (raw == null ? "" : raw.length() > 400 ? raw.substring(0, 400) + "..." : raw);
    }
}
