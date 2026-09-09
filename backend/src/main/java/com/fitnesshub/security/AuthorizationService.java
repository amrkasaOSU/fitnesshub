package com.fitnesshub.security;

import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachClientStatus;
import com.fitnesshub.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central place for "can the caller see/modify this client's data" checks.
 * Every domain service that reads or writes client-owned data (workouts, weight,
 * steps, nutrition, goals, check-ins, messages, AI history) must go through here
 * instead of re-implementing the rule, and must never trust a client id supplied
 * by the frontend without this check.
 */
@Service
public class AuthorizationService {

    private final CurrentUser currentUser;
    private final CoachClientRepository coachClientRepository;

    public AuthorizationService(CurrentUser currentUser, CoachClientRepository coachClientRepository) {
        this.currentUser = currentUser;
        this.coachClientRepository = coachClientRepository;
    }

    /**
     * Returns the client user id the caller is allowed to act as / view.
     * - A CLIENT may only ever act as themselves; {@code requestedClientId} (if present)
     *   must match their own id or is ignored in favor of it.
     * - A COACH must supply a requestedClientId and must have an active coaching
     *   relationship with that client.
     * - An ADMIN may access any client.
     */
    @Transactional(readOnly = true)
    public UUID resolveClientId(UUID requestedClientId) {
        if (currentUser.isClient()) {
            return currentUser.id();
        }
        if (requestedClientId == null) {
            throw new ForbiddenException("A clientId is required for this request.");
        }
        if (currentUser.isAdmin()) {
            return requestedClientId;
        }
        if (currentUser.isCoach()) {
            assertCoachOwnsClient(currentUser.id(), requestedClientId);
            return requestedClientId;
        }
        throw ForbiddenException.accessDenied();
    }

    @Transactional(readOnly = true)
    public void assertCoachOwnsClient(UUID coachId, UUID clientUserId) {
        boolean owns = coachClientRepository.existsByCoachIdAndClientIdAndStatus(
                coachId, clientUserId, CoachClientStatus.ACTIVE);
        if (!owns) {
            throw ForbiddenException.accessDenied();
        }
    }

    /** Throws unless the caller is the client themselves, their coach, or an admin. */
    @Transactional(readOnly = true)
    public void assertCanAccessClient(UUID clientUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isClient()) {
            if (!currentUser.id().equals(clientUserId)) {
                throw ForbiddenException.accessDenied();
            }
            return;
        }
        if (currentUser.isCoach()) {
            assertCoachOwnsClient(currentUser.id(), clientUserId);
            return;
        }
        throw ForbiddenException.accessDenied();
    }
}
