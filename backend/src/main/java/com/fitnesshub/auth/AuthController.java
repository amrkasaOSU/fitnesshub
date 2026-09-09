package com.fitnesshub.auth;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.auth.dto.LoginRequest;
import com.fitnesshub.auth.dto.RegisterRequest;
import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.security.FitnessHubUserDetails;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserMapper;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.user.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthController(AuthService authService,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository,
                           UserMapper userMapper,
                           UserRepository userRepository,
                           AuditService auditService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userMapper = userMapper;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** Forces the CSRF token cookie to be issued. The frontend calls this once before login/register. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(HttpServletRequest request) {
        request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<ApiResponse<UserDto>> register(@Valid @RequestBody RegisterRequest request,
                                                          HttpServletRequest httpRequest,
                                                          HttpServletResponse httpResponse) {
        User user = authService.register(request);
        establishSession(new LoginRequest(user.getEmail(), request.password()), httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(userMapper.toDto(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto>> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse httpResponse) {
        establishSession(request, httpRequest, httpResponse);
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElseThrow();
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.record(user.getId(), AuditAction.LOGIN);
        return ResponseEntity.ok(ApiResponse.of(userMapper.toDto(user)));
    }

    private void establishSession(LoginRequest request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    /** Exposed for tests / debugging only; real identity comes from FitnessHubUserDetails. */
    static String principalEmail(Authentication authentication) {
        return ((FitnessHubUserDetails) authentication.getPrincipal()).getUsername();
    }
}
