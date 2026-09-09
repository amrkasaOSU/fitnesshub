package com.fitnesshub.goal;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ForbiddenException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.goal.dto.CreateGoalRequest;
import com.fitnesshub.goal.dto.GoalDto;
import com.fitnesshub.goal.dto.UpdateGoalRequest;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.notification.NotificationType;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public GoalService(GoalRepository goalRepository, CurrentUser currentUser,
                        AuthorizationService authorizationService, AuditService auditService,
                        NotificationService notificationService) {
        this.goalRepository = goalRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Goal create(CreateGoalRequest request) {
        UUID clientId = currentUser.id();
        Goal goal = new Goal(clientId, request.type(), request.name(), request.targetValue(), request.unit());
        goal.setCurrentValue(request.currentValue() == null ? BigDecimal.ZERO : request.currentValue());
        goal.setTargetDate(request.targetDate());
        goal = goalRepository.save(goal);
        auditService.record(clientId, AuditAction.GOAL_CREATED, "Goal", goal.getId());
        return goal;
    }

    @Transactional(readOnly = true)
    public List<Goal> listForClient(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return goalRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    @Transactional
    public Goal update(UUID goalId, UpdateGoalRequest request) {
        Goal goal = goalRepository.findById(goalId).orElseThrow(() -> NotFoundException.of("Goal", goalId));
        if (!goal.getClientId().equals(currentUser.id()) && !currentUser.isAdmin()) {
            if (currentUser.isCoach()) {
                authorizationService.assertCoachOwnsClient(currentUser.id(), goal.getClientId());
            } else {
                throw ForbiddenException.accessDenied();
            }
        }
        if (request.name() != null) goal.setName(request.name());
        if (request.targetValue() != null) goal.setTargetValue(request.targetValue());
        if (request.currentValue() != null) goal.setCurrentValue(request.currentValue());
        if (request.targetDate() != null) goal.setTargetDate(request.targetDate());
        if (request.status() != null) goal.setStatus(request.status());

        boolean reachedTarget = goal.getStatus() == GoalStatus.ACTIVE
                && goal.getCurrentValue() != null && goal.getTargetValue() != null
                && goal.getTargetValue().compareTo(BigDecimal.ZERO) != 0
                && goal.getCurrentValue().compareTo(goal.getTargetValue()) >= 0;
        if (reachedTarget) {
            goal.setStatus(GoalStatus.ACHIEVED);
            notificationService.notify(goal.getClientId(), NotificationType.GOAL_MILESTONE,
                    "Goal achieved!", "You hit your goal: " + goal.getName() + ".", "Goal", goal.getId());
        }

        goal = goalRepository.save(goal);
        auditService.record(currentUser.id(), AuditAction.GOAL_UPDATED, "Goal", goal.getId());
        return goal;
    }

    public GoalDto toDto(Goal goal) {
        BigDecimal percent = null;
        if (goal.getTargetValue() != null && goal.getTargetValue().compareTo(BigDecimal.ZERO) != 0
                && goal.getCurrentValue() != null) {
            percent = goal.getCurrentValue().divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(100))
                    .max(BigDecimal.ZERO)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new GoalDto(goal.getId(), goal.getType(), goal.getName(), goal.getTargetValue(),
                goal.getCurrentValue(), goal.getUnit(), goal.getTargetDate(), goal.getStatus(), percent);
    }
}
