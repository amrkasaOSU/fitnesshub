package com.fitnesshub.user;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.dto.ChangePasswordRequest;
import com.fitnesshub.user.dto.UpdateUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, CurrentUser currentUser,
                        PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public User updateCurrentUser(UpdateUserRequest request) {
        User user = getCurrentUser();
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (request.profileImageUrl() != null) {
            user.setProfileImageUrl(request.profileImageUrl());
        }
        return userRepository.save(user);
    }

    /**
     * Changes the caller's own password. The current password is re-checked
     * here even though the request is authenticated, so possession of a live
     * session isn't by itself enough to lock the real owner out.
     */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ConflictException("Your current password isn't correct.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException("Your new password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.record(user.getId(), AuditAction.PASSWORD_CHANGED, "User", user.getId());
    }
}
