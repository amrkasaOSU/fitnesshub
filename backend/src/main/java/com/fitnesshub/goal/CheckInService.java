package com.fitnesshub.goal;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachClientStatus;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.goal.dto.CheckInDto;
import com.fitnesshub.goal.dto.ReviewCheckInRequest;
import com.fitnesshub.goal.dto.SubmitCheckInRequest;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CoachClientRepository coachClientRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public CheckInService(CheckInRepository checkInRepository, CoachClientRepository coachClientRepository,
                           CurrentUser currentUser, AuthorizationService authorizationService,
                           AuditService auditService) {
        this.checkInRepository = checkInRepository;
        this.coachClientRepository = coachClientRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public CheckIn submit(SubmitCheckInRequest request) {
        UUID clientId = currentUser.id();
        checkInRepository.findByClientIdAndWeekStartDate(clientId, request.weekStartDate())
                .ifPresent(existing -> {
                    throw new ConflictException("A check-in for the week of " + request.weekStartDate() + " already exists.");
                });

        CheckIn checkIn = new CheckIn(clientId, request.weekStartDate());
        checkIn.setWeight(request.weight());
        checkIn.setEnergyScore(request.energyScore());
        checkIn.setSleepScore(request.sleepScore());
        checkIn.setStressScore(request.stressScore());
        checkIn.setHungerScore(request.hungerScore());
        checkIn.setWorkoutAdherence(request.workoutAdherence());
        checkIn.setNutritionAdherence(request.nutritionAdherence());
        checkIn.setNotes(request.notes());
        checkIn = checkInRepository.save(checkIn);
        auditService.record(clientId, AuditAction.CHECKIN_SUBMITTED, "CheckIn", checkIn.getId());
        return checkIn;
    }

    @Transactional(readOnly = true)
    public Page<CheckIn> history(UUID requestedClientId, Pageable pageable) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return checkInRepository.findByClientIdOrderByWeekStartDateDesc(clientId, pageable);
    }

    /** Check-ins from any of the coach's active clients that the coach hasn't reviewed yet. */
    @Transactional(readOnly = true)
    public List<CheckIn> pendingReviewForCoach(UUID coachId) {
        List<UUID> clientIds = coachClientRepository.findByCoachId(coachId).stream()
                .filter(cc -> cc.getStatus() == CoachClientStatus.ACTIVE)
                .map(cc -> cc.getClientId())
                .collect(Collectors.toList());
        if (clientIds.isEmpty()) {
            return List.of();
        }
        return checkInRepository.findByClientIdInAndCoachReviewedAtIsNullOrderBySubmittedAtAsc(clientIds);
    }

    @Transactional
    public CheckIn review(UUID checkInId, ReviewCheckInRequest request) {
        CheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> NotFoundException.of("CheckIn", checkInId));
        authorizationService.assertCoachOwnsClient(currentUser.id(), checkIn.getClientId());

        checkIn.setCoachReviewedAt(Instant.now());
        checkIn.setCoachResponse(request.coachResponse());
        checkIn = checkInRepository.save(checkIn);
        auditService.record(currentUser.id(), AuditAction.CHECKIN_REVIEWED, "CheckIn", checkIn.getId());
        return checkIn;
    }

    public CheckInDto toDto(CheckIn c) {
        return new CheckInDto(c.getId(), c.getClientId(), c.getWeekStartDate(), c.getWeight(), c.getEnergyScore(),
                c.getSleepScore(), c.getStressScore(), c.getHungerScore(), c.getWorkoutAdherence(),
                c.getNutritionAdherence(), c.getNotes(), c.getSubmittedAt(), c.getCoachReviewedAt(),
                c.getCoachResponse(), c.getCoachReviewedAt() == null);
    }
}
