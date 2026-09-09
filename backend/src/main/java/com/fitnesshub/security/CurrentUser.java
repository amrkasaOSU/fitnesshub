package com.fitnesshub.security;

import com.fitnesshub.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for "who is making this request". Every service that
 * enforces ownership must derive the caller's identity from here, never from a
 * client-supplied path/query/body parameter.
 */
@Component
public class CurrentUser {

    public UUID id() {
        return details().getUserId();
    }

    public Role role() {
        return Role.valueOf(details().getRole());
    }

    public boolean isCoach() {
        return role() == Role.COACH;
    }

    public boolean isClient() {
        return role() == Role.CLIENT;
    }

    public boolean isAdmin() {
        return role() == Role.ADMIN;
    }

    private FitnessHubUserDetails details() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FitnessHubUserDetails details)) {
            throw new IllegalStateException("No authenticated FitnessHub user in security context");
        }
        return details;
    }
}
