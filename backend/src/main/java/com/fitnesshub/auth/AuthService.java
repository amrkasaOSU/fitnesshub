package com.fitnesshub.auth;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.auth.dto.RegisterRequest;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.coach.CoachClient;
import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachProfile;
import com.fitnesshub.coach.CoachProfileRepository;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRepository coachClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                        CoachProfileRepository coachProfileRepository,
                        ClientProfileRepository clientProfileRepository,
                        CoachClientRepository coachClientRepository,
                        PasswordEncoder passwordEncoder,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.coachClientRepository = coachClientRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ConflictException("Admin accounts cannot be self-registered.");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.role()
        );
        user = userRepository.save(user);

        if (request.role() == Role.COACH) {
            coachProfileRepository.save(new CoachProfile(user.getId()));
        } else {
            if (request.coachId() == null) {
                throw new ConflictException("A coachId (invite link) is required to register as a client.");
            }
            User coachUser = userRepository.findById(request.coachId())
                    .orElseThrow(() -> new NotFoundException("Coach not found: " + request.coachId()));
            if (coachUser.getRole() != Role.COACH) {
                throw new ConflictException("The referenced account is not a coach.");
            }
            ClientProfile profile = new ClientProfile(user.getId(), coachUser.getId());
            clientProfileRepository.save(profile);
            coachClientRepository.save(new CoachClient(coachUser.getId(), user.getId()));
        }

        auditService.record(user.getId(), AuditAction.USER_REGISTERED, "User", user.getId());
        return user;
    }
}
