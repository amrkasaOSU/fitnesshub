package com.fitnesshub.notification.jobs;

import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.goal.CheckInRepository;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.notification.NotificationType;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.ClientProgramStatus;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
import com.fitnesshub.program.ProgramExerciseRepository;
import com.fitnesshub.subscription.Subscription;
import com.fitnesshub.subscription.SubscriptionRepository;
import com.fitnesshub.subscription.SubscriptionStatus;
import com.fitnesshub.subscription.SubscriptionTier;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily background jobs (spec section 124). Each client is processed
 * independently inside a try/catch so one bad record never stops the rest of
 * the run.
 */
@Component
public class ReminderJobs {

    private static final Logger log = LoggerFactory.getLogger(ReminderJobs.class);

    private final UserRepository userRepository;
    private final CheckInRepository checkInRepository;
    private final ClientProgramRepository clientProgramRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

    public ReminderJobs(UserRepository userRepository, CheckInRepository checkInRepository,
                         ClientProgramRepository clientProgramRepository, ProgramDayRepository programDayRepository,
                         ProgramExerciseRepository programExerciseRepository,
                         WorkoutSessionRepository workoutSessionRepository,
                         SubscriptionRepository subscriptionRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.checkInRepository = checkInRepository;
        this.clientProgramRepository = clientProgramRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationService = notificationService;
    }

    /** 9am UTC daily: nudge clients who haven't submitted a check-in in over a week. */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendCheckInReminders() {
        for (User client : userRepository.findAll()) {
            if (client.getRole() != com.fitnesshub.user.Role.CLIENT) {
                continue;
            }
            try {
                Optional<CheckIn> last = checkInRepository.findFirstByClientIdOrderByWeekStartDateDesc(client.getId());
                boolean overdue = last.map(c -> c.getSubmittedAt().isBefore(Instant.now().minus(7, ChronoUnit.DAYS)))
                        .orElse(true);
                if (overdue) {
                    notificationService.notify(client.getId(), NotificationType.CHECKIN_REMINDER,
                            "Weekly check-in due", "It's been a week - submit your check-in when you get a chance.",
                            "CheckInReminder", currentWeekMarker(client.getId()));
                }
            } catch (Exception e) {
                log.warn("Check-in reminder failed for client {}", client.getId(), e);
            }
        }
    }

    /** 6pm UTC daily: nudge clients with a scheduled training day today who haven't logged anything. */
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void sendWorkoutReminders() {
        for (User client : userRepository.findAll()) {
            if (client.getRole() != com.fitnesshub.user.Role.CLIENT) {
                continue;
            }
            try {
                ZoneId zone = safeZone(client.getTimezone());
                LocalDate today = LocalDate.now(zone);
                Optional<ClientProgram> active = clientProgramRepository
                        .findFirstByClientIdAndStatusOrderByStartDateDesc(client.getId(), ClientProgramStatus.ACTIVE);
                if (active.isEmpty()) {
                    continue;
                }
                List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(active.get().getProgramId());
                if (days.isEmpty()) {
                    continue;
                }
                long daysSinceStart = ChronoUnit.DAYS.between(active.get().getStartDate(), today);
                if (daysSinceStart < 0) {
                    continue;
                }
                ProgramDay todayDay = days.get((int) (daysSinceStart % days.size()));
                boolean isTrainingDay = !programExerciseRepository
                        .findByProgramDayIdOrderByOrderIndexAsc(todayDay.getId()).isEmpty();
                if (!isTrainingDay) {
                    continue;
                }

                Instant startOfDay = today.atStartOfDay(zone).toInstant();
                Instant now = Instant.now();
                boolean loggedToday = !workoutSessionRepository
                        .findByClientIdAndStartedAtBetween(client.getId(), startOfDay, now).isEmpty();
                if (!loggedToday) {
                    notificationService.notify(client.getId(), NotificationType.WORKOUT_REMINDER,
                            "Today's workout: " + todayDay.getName(),
                            "You've got " + todayDay.getName() + " on the schedule today.",
                            "ProgramDay", todayDay.getId());
                }
            } catch (Exception e) {
                log.warn("Workout reminder failed for client {}", client.getId(), e);
            }
        }
    }

    /** 9am UTC daily: remind users whose paid plan renews within 3 days. */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendSubscriptionRenewalReminders() {
        Instant soon = Instant.now().plus(3, ChronoUnit.DAYS);
        for (Subscription sub : subscriptionRepository.findAll()) {
            try {
                if (sub.getTier() == SubscriptionTier.FREE || sub.getStatus() != SubscriptionStatus.ACTIVE
                        || sub.getCurrentPeriodEnd() == null) {
                    continue;
                }
                if (sub.getCurrentPeriodEnd().isBefore(soon) && sub.getCurrentPeriodEnd().isAfter(Instant.now())) {
                    notificationService.notify(sub.getUserId(), NotificationType.SUBSCRIPTION,
                            "Your plan renews soon",
                            "Your FitnessHub subscription renews on " + sub.getCurrentPeriodEnd() + ".",
                            "Subscription", sub.getId());
                }
            } catch (Exception e) {
                log.warn("Subscription reminder failed for subscription {}", sub.getId(), e);
            }
        }
    }

    /** A stable per-week identifier so the notification dedupe guard fires at most once per client per week. */
    private UUID currentWeekMarker(UUID clientId) {
        LocalDate monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return UUID.nameUUIDFromBytes((clientId.toString() + monday).getBytes());
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
