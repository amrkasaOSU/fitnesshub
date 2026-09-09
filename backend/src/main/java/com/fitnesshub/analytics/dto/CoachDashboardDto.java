package com.fitnesshub.analytics.dto;

import com.fitnesshub.coach.dto.ClientSummaryDto;
import com.fitnesshub.progress.dto.PersonalRecordDto;

import java.math.BigDecimal;
import java.util.List;

public record CoachDashboardDto(
        int totalClients,
        int activeClients,
        int clientsTrainingToday,
        BigDecimal averageWeeklyAdherence,
        BigDecimal averageWeeklyWeightChange,
        List<ClientSummaryDto> clientsNeedingAttention,
        List<PersonalRecordDto> recentPrs,
        long unreadMessages,
        long checkInsPendingReview
) {
}
