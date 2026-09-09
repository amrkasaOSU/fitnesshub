package com.fitnesshub.bodyweight;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeightServiceTest {

    private final WeightService service = new WeightService(null, null, null, null, null, null);
    private final ZoneId zone = ZoneId.of("America/New_York");
    private final UUID clientId = UUID.randomUUID();

    private WeightEntry entryOn(LocalDate date, String weight) {
        Instant at = date.atTime(8, 0).atZone(zone).toInstant();
        return new WeightEntry(clientId, new BigDecimal(weight), at);
    }

    @Test
    void weeklyAverage_averagesOnlyEntriesWithinThatMondayToSundayWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 3); // a Monday
        List<WeightEntry> entries = List.of(
                entryOn(monday, "180.0"),
                entryOn(monday.plusDays(3), "178.0"),  // Thursday, same week
                entryOn(monday.plusDays(6), "177.0"),  // Sunday, same week
                entryOn(monday.plusDays(7), "999.0")   // next Monday - must be excluded
        );

        BigDecimal avg = service.weeklyAverage(entries, zone, monday);

        assertThat(avg).isEqualByComparingTo("178.33");
    }

    @Test
    void weeklyAverage_noEntriesInWeek_returnsNull() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        List<WeightEntry> entries = List.of(entryOn(monday.minusDays(10), "180.0"));

        assertThat(service.weeklyAverage(entries, zone, monday)).isNull();
    }
}
