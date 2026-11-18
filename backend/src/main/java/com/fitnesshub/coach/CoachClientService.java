package com.fitnesshub.coach;

import com.fitnesshub.analytics.AdherenceService;
import com.fitnesshub.analytics.AttentionFlagService;
import com.fitnesshub.analytics.ClientSnapshot;
import com.fitnesshub.analytics.ClientSnapshotLoader;
import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.bodyweight.WeightService;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.coach.dto.ClientSummaryDto;
import com.fitnesshub.coach.dto.CreateClientRequest;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CoachClientService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRepository coachClientRepository;
    private final CoachNoteRepository coachNoteRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final WeightService weightService;
    private final AttentionFlagService attentionFlagService;
    private final AdherenceService adherenceService;
    private final ClientSnapshotLoader snapshotLoader;

    public CoachClientService(UserRepository userRepository,
                               ClientProfileRepository clientProfileRepository,
                               CoachClientRepository coachClientRepository,
                               CoachNoteRepository coachNoteRepository,
                               PasswordEncoder passwordEncoder,
                               CurrentUser currentUser,
                               AuthorizationService authorizationService,
                               AuditService auditService,
                               WeightService weightService,
                               AttentionFlagService attentionFlagService,
                               AdherenceService adherenceService,
                               ClientSnapshotLoader snapshotLoader) {
        this.userRepository = userRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.coachClientRepository = coachClientRepository;
        this.coachNoteRepository = coachNoteRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.weightService = weightService;
        this.attentionFlagService = attentionFlagService;
        this.adherenceService = adherenceService;
        this.snapshotLoader = snapshotLoader;
    }

    /**
     * Sets a temporary password for a client who can't get in. There's no email
     * delivery in this system, so account recovery deliberately runs through the
     * coach - who already knows the client personally and can hand it over
     * directly. Restricted to the coach who actually owns this client.
     */
    @Transactional
    public void resetClientPassword(UUID clientId, String temporaryPassword) {
        UUID coachId = currentUser.id();
        authorizationService.assertCoachOwnsClient(coachId, clientId);
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client not found."));
        client.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        userRepository.save(client);
        auditService.record(coachId, AuditAction.CLIENT_PASSWORD_RESET, "User", clientId);
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
        List<ClientSummaryDto> summaries = summarizeAll(List.of(clientId));
        if (summaries.isEmpty()) {
            throw NotFoundException.of("User", clientId);
        }
        return summaries.get(0);
    }

    /**
     * Roster rows for many clients at once, in the order the ids were given.
     *
     * <p>Everything is read through {@link ClientSnapshotLoader} - one query
     * per table for the whole list - and the figures are then computed in
     * memory by the same functions the client's own dashboard uses
     * ({@link WeightService#weeklyAverage}, {@link AdherenceService#calculate},
     * {@link AttentionFlagService#evaluate}), so the coach and the client can
     * never be shown different numbers for the same metric. The cost is the
     * same handful of queries for 5 clients or 500.
     *
     * <p>Callers are responsible for passing only ids the caller may see; this
     * method is fed from the coach's own client list.
     */
    @Transactional(readOnly = true)
    public List<ClientSummaryDto> summarizeAll(List<UUID> clientIds) {
        Instant now = Instant.now();
        return snapshotLoader.load(clientIds).values().stream()
                .map(snapshot -> toSummary(snapshot, now))
                .toList();
    }

    /** Snapshots for a coach's active clients, for callers that need more than the summary row. */
    @Transactional(readOnly = true)
    public Map<UUID, ClientSnapshot> snapshotsFor(List<UUID> clientIds) {
        return snapshotLoader.load(clientIds);
    }

    public ClientSummaryDto toSummary(ClientSnapshot snapshot, Instant now) {
        User user = snapshot.user();
        ClientProfile profile = snapshot.profile();
        ZoneId zone = snapshot.zone();
        LocalDate today = LocalDate.ofInstant(now, zone);

        BigDecimal currentWeight = snapshot.latestWeight() == null ? null : snapshot.latestWeight().getWeight();
        BigDecimal weeklyWeightChange = weightService.weeklyChange(snapshot.recentWeights(), zone, today);

        AdherenceResult adherence = adherenceService.calculateFromSnapshot(snapshot, today.minusDays(6), today, zone);

        int todaySteps = snapshot.recentSteps().stream()
                .filter(s -> s.getDate().equals(today))
                .map(StepEntry::getSteps)
                .findFirst().orElse(0);
        BigDecimal caloriesToday = snapshot.recentNutrition().stream()
                .filter(n -> n.getDate().equals(today))
                .map(NutritionEntry::getCalories)
                .findFirst().orElse(BigDecimal.ZERO);

        var flags = attentionFlagService.evaluate(snapshot, zone, now);

        return new ClientSummaryDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                profile == null ? null : profile.getFitnessGoal(),
                currentWeight, weeklyWeightChange,
                adherence.adherencePercentage(),
                snapshot.lastCompletedWorkoutAt(), todaySteps, caloriesToday,
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

}
