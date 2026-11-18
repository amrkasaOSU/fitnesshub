package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.analytics.dto.ClientDashboardDto;
import com.fitnesshub.analytics.dto.CoachDashboardDto;
import com.fitnesshub.bodyweight.WeightService;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.coach.CoachClient;
import com.fitnesshub.coach.CoachClientStatus;
import com.fitnesshub.coach.CoachClientService;
import com.fitnesshub.coach.dto.ClientSummaryDto;
import com.fitnesshub.goal.GoalRepository;
import com.fitnesshub.goal.GoalService;
import com.fitnesshub.goal.GoalStatus;
import com.fitnesshub.goal.CheckInService;
import com.fitnesshub.messaging.ConversationRepository;
import com.fitnesshub.messaging.MessageRepository;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.nutrition.NutritionService;
import com.fitnesshub.progress.PersonalRecordService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepService;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutService;
import com.fitnesshub.workout.WorkoutSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final WorkoutService workoutService;
    private final NutritionService nutritionService;
    private final StepService stepService;
    private final WeightService weightService;
    private final AdherenceService adherenceService;
    private final PersonalRecordService personalRecordService;
    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final CoachClientService coachClientService;
    private final CheckInService checkInService;
    private final ClientProfileRepository clientProfileRepository;

    public DashboardService(CurrentUser currentUser, UserRepository userRepository, WorkoutService workoutService,
                             NutritionService nutritionService, StepService stepService, WeightService weightService,
                             AdherenceService adherenceService, PersonalRecordService personalRecordService,
                             GoalRepository goalRepository, GoalService goalService,
                             ConversationRepository conversationRepository, MessageRepository messageRepository,
                             NotificationService notificationService, CoachClientService coachClientService,
                             CheckInService checkInService,
                             ClientProfileRepository clientProfileRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.workoutService = workoutService;
        this.nutritionService = nutritionService;
        this.stepService = stepService;
        this.weightService = weightService;
        this.adherenceService = adherenceService;
        this.personalRecordService = personalRecordService;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
        this.coachClientService = coachClientService;
        this.checkInService = checkInService;
        this.clientProfileRepository = clientProfileRepository;
    }

    /**
     * Not read-only: the first dashboard load of the day creates today's
     * workout session, exactly as {@code GET /api/workouts/today} does. Under
     * a read-only transaction that insert was silently never flushed, so the
     * dashboard handed back a session id that did not exist.
     */
    @Transactional
    public ClientDashboardDto clientDashboard() {
        UUID clientId = currentUser.id();
        User user = userRepository.findById(clientId).orElseThrow();
        ZoneId zone = safeZone(user.getTimezone());
        LocalDate today = LocalDate.now(zone);

        var todaySession = workoutService.toDto(workoutService.getOrCreateTodaySession(clientId, user.getTimezone()));
        var nutrition = nutritionService.dashboard(clientId);
        var steps = stepService.dashboard(clientId);
        var weight = weightService.dashboard(clientId);
        AdherenceResult adherence = adherenceService.calculateForClient(clientId, today.minusDays(6), today, zone);
        var recentPrs = personalRecordService.recentForClient(clientId, 5);
        var goals = goalRepository.findByClientIdAndStatus(clientId, GoalStatus.ACTIVE).stream()
                .map(goalService::toDto).toList();

        long unreadMessages = clientProfileRepository.findByUserId(clientId)
                .flatMap(profile -> conversationRepository.findByCoachIdAndClientId(profile.getCoachId(), clientId))
                .map(conv -> messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conv.getId(), clientId))
                .orElse(0L);
        long unreadNotifications = notificationService.unreadCount();

        return new ClientDashboardDto(todaySession, nutrition, steps, weight, adherence, recentPrs, goals,
                unreadMessages, unreadNotifications);
    }

    /**
     * The coach's landing page. Costs a fixed number of queries however many
     * clients the coach has: the roster comes from one batched snapshot load
     * (see {@link ClientSnapshotLoader}) and each remaining figure is a single
     * aggregate query rather than a loop over clients or conversations.
     */
    @Transactional(readOnly = true)
    public CoachDashboardDto coachDashboard() {
        UUID coachId = currentUser.id();
        List<CoachClient> all = coachClientService.allClients(coachId);
        List<UUID> activeIds = all.stream()
                .filter(cc -> cc.getStatus() == CoachClientStatus.ACTIVE)
                .map(CoachClient::getClientId)
                .toList();

        Instant now = Instant.now();
        var snapshots = coachClientService.snapshotsFor(activeIds);
        List<ClientSummaryDto> summaries = snapshots.values().stream()
                .map(snapshot -> coachClientService.toSummary(snapshot, now))
                .toList();

        Instant dayAgo = now.minus(1, ChronoUnit.DAYS);
        int trainingToday = (int) snapshots.values().stream()
                .filter(snapshot -> snapshot.recentSessions().stream().anyMatch(s ->
                        s.getStatus() != WorkoutSessionStatus.NOT_STARTED
                                && !s.getStartedAt().isBefore(dayAgo) && !s.getStartedAt().isAfter(now)))
                .count();

        List<BigDecimal> adherenceValues = summaries.stream()
                .map(ClientSummaryDto::adherencePercentage).filter(v -> v != null).toList();
        BigDecimal avgAdherence = average(adherenceValues);

        List<BigDecimal> weightChanges = summaries.stream()
                .map(ClientSummaryDto::weeklyWeightChange).filter(v -> v != null).toList();
        BigDecimal avgWeightChange = average(weightChanges);

        List<ClientSummaryDto> needingAttention = summaries.stream()
                .filter(s -> !s.attentionFlags().isEmpty())
                .toList();

        var recentPrs = personalRecordService.recentForClients(activeIds, 10);
        long unreadMessages = messageRepository.countUnreadForCoach(coachId);
        long pendingCheckIns = checkInService.countPendingReview(activeIds);

        return new CoachDashboardDto(all.size(), activeIds.size(), trainingToday, avgAdherence, avgWeightChange,
                needingAttention, recentPrs, unreadMessages, pendingCheckIns);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
