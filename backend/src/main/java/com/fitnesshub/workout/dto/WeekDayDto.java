package com.fitnesshub.workout.dto;

import com.fitnesshub.workout.WorkoutSessionStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One square in the client's Monday-Sunday week strip. {@code programDayName}
 * is what the shifted schedule lands on for this date, which after a skip is
 * not the same as what the untouched cycle would have prescribed.
 */
public record WeekDayDto(
        LocalDate date,
        String dayOfWeek,
        boolean today,
        boolean past,
        UUID programDayId,
        String programDayName,
        boolean restDay,
        UUID sessionId,
        WorkoutSessionStatus status,
        String skipReason,
        boolean shifted
) {
}
