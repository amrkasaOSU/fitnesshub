package com.fitnesshub.user;

import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.dto.UpdateUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
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
}
