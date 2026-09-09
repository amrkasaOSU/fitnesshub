package com.fitnesshub.progress;

import com.fitnesshub.coach.CoachClient;
import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachClientStatus;
import com.fitnesshub.exercise.Exercise;
import com.fitnesshub.exercise.ExerciseRepository;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.notification.NotificationType;
import com.fitnesshub.progress.dto.PersonalRecordDto;
import com.fitnesshub.workout.WorkoutSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects and persists new personal records. Called by WorkoutService right
 * after a set is completed (MAX_WEIGHT / MAX_REPS / ESTIMATED_1RM) and again
 * when a workout is completed (MAX_VOLUME, which is a whole-session figure).
 * Every new record also notifies the client and, if the coach's notification
 * preferences allow it, the coach.
 */
@Service
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final ProgressionService progressionService;
    private final ExerciseRepository exerciseRepository;
    private final CoachClientRepository coachClientRepository;
    private final NotificationService notificationService;

    public PersonalRecordService(PersonalRecordRepository personalRecordRepository,
                                  ProgressionService progressionService,
                                  ExerciseRepository exerciseRepository,
                                  CoachClientRepository coachClientRepository,
                                  NotificationService notificationService) {
        this.personalRecordRepository = personalRecordRepository;
        this.progressionService = progressionService;
        this.exerciseRepository = exerciseRepository;
        this.coachClientRepository = coachClientRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<PersonalRecord> checkSetForRecords(UUID clientId, UUID exerciseId, WorkoutSet set) {
        List<PersonalRecord> created = new ArrayList<>();
        if (!set.isValidForOneRepMaxAndPr()) {
            return created;
        }

        maybeRecord(clientId, exerciseId, RecordType.MAX_WEIGHT, set.getWeight(), set.getId(), set.getCompletedAt())
                .ifPresent(created::add);
        maybeRecord(clientId, exerciseId, RecordType.MAX_REPS, BigDecimal.valueOf(set.getReps()), set.getId(), set.getCompletedAt())
                .ifPresent(created::add);
        BigDecimal est1Rm = progressionService.estimated1Rm(set.getWeight(), set.getReps());
        maybeRecord(clientId, exerciseId, RecordType.ESTIMATED_1RM, est1Rm, set.getId(), set.getCompletedAt())
                .ifPresent(created::add);

        created.forEach(pr -> notifyNewRecord(clientId, exerciseId, pr));
        return created;
    }

    @Transactional
    public Optional<PersonalRecord> checkSessionVolumeForRecord(UUID clientId, UUID exerciseId,
                                                                  List<WorkoutSet> setsInSession,
                                                                  UUID representativeSetId, Instant achievedAt) {
        BigDecimal volume = progressionService.totalVolume(setsInSession);
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        Optional<PersonalRecord> pr = maybeRecord(clientId, exerciseId, RecordType.MAX_VOLUME, volume, representativeSetId, achievedAt);
        pr.ifPresent(record -> notifyNewRecord(clientId, exerciseId, record));
        return pr;
    }

    private Optional<PersonalRecord> maybeRecord(UUID clientId, UUID exerciseId, RecordType type,
                                                  BigDecimal value, UUID setId, Instant achievedAt) {
        Optional<PersonalRecord> existing = personalRecordRepository
                .findByClientIdAndExerciseIdAndRecordType(clientId, exerciseId, type);

        if (existing.isPresent()) {
            if (value.compareTo(existing.get().getValue()) <= 0) {
                return Optional.empty();
            }
            PersonalRecord record = existing.get();
            record.setValue(value);
            record.setWorkoutSetId(setId);
            record.setAchievedAt(achievedAt);
            return Optional.of(personalRecordRepository.save(record));
        }

        return Optional.of(personalRecordRepository.save(
                new PersonalRecord(clientId, exerciseId, type, value, setId, achievedAt)));
    }

    private void notifyNewRecord(UUID clientId, UUID exerciseId, PersonalRecord pr) {
        String exerciseName = exerciseRepository.findById(exerciseId).map(e -> e.getName()).orElse("your exercise");
        String title = "New PR!";
        String body = describeRecord(exerciseName, pr);

        notificationService.notify(clientId, NotificationType.PR_ACHIEVED, title, body,
                "PersonalRecord", pr.getId());

        coachClientRepository.findFirstByClientIdAndStatus(clientId, CoachClientStatus.ACTIVE)
                .ifPresent(cc -> notificationService.notify(cc.getCoachId(), NotificationType.PR_ACHIEVED,
                        "Client PR: " + exerciseName, body, "PersonalRecord", pr.getId()));
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordDto> recentForClient(UUID clientId, int limit) {
        List<PersonalRecord> records = personalRecordRepository.findByClientIdOrderByAchievedAtDesc(clientId);
        return toDtos(records.stream().limit(limit).toList());
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordDto> recentForCoach(UUID coachId, int limit) {
        List<UUID> clientIds = coachClientRepository.findByCoachId(coachId).stream()
                .filter(cc -> cc.getStatus() == CoachClientStatus.ACTIVE)
                .map(CoachClient::getClientId)
                .toList();
        List<PersonalRecord> records = clientIds.stream()
                .flatMap(id -> personalRecordRepository.findByClientIdOrderByAchievedAtDesc(id).stream())
                .sorted(Comparator.comparing(PersonalRecord::getAchievedAt).reversed())
                .limit(limit)
                .toList();
        return toDtos(records);
    }

    private List<PersonalRecordDto> toDtos(List<PersonalRecord> records) {
        Map<UUID, String> exerciseNames = exerciseRepository
                .findAllById(records.stream().map(PersonalRecord::getExerciseId).distinct().toList())
                .stream().collect(Collectors.toMap(Exercise::getId, Exercise::getName));
        return records.stream().map(pr -> new PersonalRecordDto(pr.getId(), pr.getClientId(), pr.getExerciseId(),
                exerciseNames.getOrDefault(pr.getExerciseId(), "Exercise"), pr.getRecordType(),
                pr.getValue().stripTrailingZeros().toPlainString(), pr.getAchievedAt())).toList();
    }

    private String describeRecord(String exerciseName, PersonalRecord pr) {
        return switch (pr.getRecordType()) {
            case MAX_WEIGHT -> "You hit " + pr.getValue().stripTrailingZeros().toPlainString() + " on " + exerciseName + ".";
            case MAX_REPS -> "You hit " + pr.getValue().intValue() + " reps on " + exerciseName + ".";
            case ESTIMATED_1RM -> "Your estimated 1RM on " + exerciseName + " is now " + pr.getValue().stripTrailingZeros().toPlainString() + ".";
            case MAX_VOLUME -> "New volume PR on " + exerciseName + ": " + pr.getValue().stripTrailingZeros().toPlainString() + ".";
        };
    }
}
