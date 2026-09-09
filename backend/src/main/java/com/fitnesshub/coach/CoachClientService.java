package com.fitnesshub.coach;

import com.fitnesshub.analytics.AdherenceService;
import com.fitnesshub.analytics.AttentionFlagService;
import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.bodyweight.WeightService;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.coach.dto.ClientSummaryDto;
import com.fitnesshub.coach.dto.CreateClientRequest;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.nutrition.NutritionService;
import com.fitnesshub.nutrition.dto.NutritionDashboardDto;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepService;
import com.fitnesshub.steps.dto.StepDashboardDto;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class CoachClientService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRepository coachClientRepository;
    private final CoachNoteRepository coachNoteRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final WeightService weightService;
    private final StepService stepService;
    private final NutritionService nutritionService;
    private final AttentionFlagService attentionFlagService;
    private final AdherenceService adherenceService;

    public CoachClientService(UserRepository userRepository,
                               ClientProfileRepository clientProfileRepository,
                               CoachClientRepository coachClientRepository,
                               CoachNoteRepository coachNoteRepository,
                               WorkoutSessionRepository workoutSessionRepository,
                               PasswordEncoder passwordEncoder,
                               CurrentUser currentUser,
                               AuthorizationService authorizationService,
                               AuditService auditService,
                               WeightService weightService,
                               StepService stepService,
                               NutritionService nutritionService,
                               AttentionFlagService attentionFlagService,
                               AdherenceService adherenceService) {
        this.userRepository = userRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.coachClientRepository = coachClientRepository;
        this.coachNoteRepository = coachNoteRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.weightService = weightService;
        this.stepService = stepService;
        this.nutritionService = nutritionService;
        this.attentionFlagService = attentionFlagService;
        this.adherenceService = adherenceService;
    }

    @Transactional
    public User createClient(CreateClientRequest request) {
        UUID coachId = currentUser.id();
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists.");
        }
        User client = new User(request.email().toLowerCase(),
                passwordEncoder.encode(request.temporaryPassword()),
                request.firstName(), request.lastName(), Role.CLIENT);
        client = userRepository.save(client);

        ClientProfile profile = new ClientProfile(client.getId(), coachId);
        profile.setFitnessGoal(request.fitnessGoal());
        if (request.activityLevel() != null) profile.setActivityLevel(request.activityLevel());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setHeightCm(request.heightCm());
        profile.setSex(request.sex());
        profile.setStartingWeight(request.startingWeight());
        profile.setTargetWeight(request.targetWeight());
        profile.setDailyCalorieTarget(request.dailyCalorieTarget());
        profile.setDailyProteinTarget(request.dailyProteinTarget());
        if (request.dailyStepTarget() != null) profile.setDailyStepTarget(request.dailyStepTarget());
        clientProfileRepository.save(profile);

        coachClientRepository.save(new CoachClient(coachId, client.getId()));
        auditService.record(coachId, AuditAction.CLIENT_CREATED, "User", client.getId());
        return client;
    }

    @Transactional(readOnly = true)
    public List<CoachClient> allClients(UUID coachId) {
        return coachClientRepository.findByCoachId(coachId);
    }

    @Transactional(readOnly = true)
    public List<CoachClient> activeClients(UUID coachId) {
        return coachClientRepository.findByCoachId(coachId).stream()
                .filter(cc -> cc.getStatus() == CoachClientStatus.ACTIVE)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientSummaryDto summarize(UUID clientId) {
        User user = userRepository.findById(clientId).orElseThrow(() -> NotFoundException.of("User", clientId));
        ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);

        WeightDashboardDto weight = weightService.dashboard(clientId);
        StepDashboardDto steps = stepService.dashboard(clientId);
        NutritionDashboardDto nutrition = nutritionService.dashboard(clientId);

        ZoneId zone = safeZone(user.getTimezone());
        LocalDate today = LocalDate.now(zone);
        AdherenceResult adherence = adherenceService.calculateForClient(clientId, today.minusDays(6), today, zone);

        var flags = attentionFlagService.computeFlags(clientId, zone);
        Instant lastWorkout = workoutSessionRepository
                .findFirstByClientIdAndStatusOrderByStartedAtDesc(clientId, WorkoutSessionStatus.COMPLETED)
                .map(com.fitnesshub.workout.WorkoutSession::getStartedAt).orElse(null);

        return new ClientSummaryDto(clientId, user.getFirstName(), user.getLastName(), user.getEmail(),
                profile == null ? null : profile.getFitnessGoal(),
                weight.current(), weight.weeklyChange(),
                adherence.adherencePercentage(),
                lastWorkout, steps.todaySteps(), nutrition.caloriesConsumedToday(),
                flags.stream().map(Enum::name).toList());
    }

    @Transactional
    public CoachNote addNote(UUID clientId, String content) {
        UUID coachId = currentUser.id();
        authorizationService.assertCoachOwnsClient(coachId, clientId);
        CoachNote note = coachNoteRepository.save(new CoachNote(coachId, clientId, content));
        auditService.record(coachId, AuditAction.COACH_NOTE_CREATED, "CoachNote", note.getId());
        return note;
    }

    @Transactional(readOnly = true)
    public List<CoachNote> listNotes(UUID clientId) {
        UUID coachId = currentUser.id();
        authorizationService.assertCoachOwnsClient(coachId, clientId);
        return coachNoteRepository.findByCoachIdAndClientIdOrderByCreatedAtDesc(coachId, clientId);
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
