package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.FitnessContext;
import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.progress.RecordType;
import com.fitnesshub.progress.dto.PersonalRecordDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model can only answer from what it is sent, so the context JSON is the
 * whole point of the AI pipeline. This pins that a realistic context - one
 * containing timestamps, as every client with a personal record has - is
 * actually serialised, rather than silently replaced with "{}".
 *
 * <p>Uses the same builder Spring Boot configures its ObjectMapper with. Run
 * against the provider's old bare {@code new ObjectMapper()}, both tests
 * failed: the JSON was exactly "{}".
 */
class OpenAiProviderContextTest {

    private static FitnessContext contextWithARecentPr() {
        var pr = new PersonalRecordDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Bench Press", RecordType.MAX_WEIGHT, "205", Instant.parse("2026-11-10T18:30:00Z"));
        var weight = new WeightDashboardDto(new BigDecimal("200.1"), new BigDecimal("205"), new BigDecimal("199.4"),
                new BigDecimal("205.6"), new BigDecimal("200.37"), null, new BigDecimal("-0.45"), null,
                new BigDecimal("-4.90"), new BigDecimal("225"));
        return new FitnessContext("Alex", null, null, List.of(pr), new AdherenceResult(3, 4, new BigDecimal("75.0")),
                weight, null, null, List.of(), 14);
    }

    /** Built like the application's: application.yml sets write-dates-as-timestamps: false. */
    private static com.fasterxml.jackson.databind.ObjectMapper appObjectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Test
    void theClientsDataReachesThePrompt() {
        String json = new OpenAiProvider(appObjectMapper()).toJson(contextWithARecentPr());

        assertThat(json).isNotEqualTo("{}");
        assertThat(json).contains("\"current\":200.1").contains("Bench Press").contains("\"completedWorkouts\":3");
    }

    @Test
    void timestampsAreReadableDatesNotEpochNumbers() {
        String json = new OpenAiProvider(appObjectMapper()).toJson(contextWithARecentPr());

        assertThat(json).contains("2026-11-10T18:30:00Z");
    }
}
