package com.fitnesshub.workout;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.dto.CompleteWorkoutRequest;
import com.fitnesshub.workout.dto.CreateFeedbackRequest;
import com.fitnesshub.workout.dto.CreateWorkoutRequest;
import com.fitnesshub.workout.dto.LogSetRequest;
import com.fitnesshub.workout.dto.SetCompletionResult;
import com.fitnesshub.workout.dto.SkipWorkoutRequest;
import com.fitnesshub.workout.dto.WeekDayDto;
import com.fitnesshub.workout.dto.WorkoutFeedbackDto;
import com.fitnesshub.workout.dto.WorkoutSessionDto;
import com.fitnesshub.workout.dto.WorkoutSummaryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public WorkoutController(WorkoutService workoutService, CurrentUser currentUser,
                              AuthorizationService authorizationService, UserRepository userRepository) {
        this.workoutService = workoutService;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/today")
    public ApiResponse<WorkoutSessionDto> today() {
        User user = userRepository.findById(currentUser.id()).orElseThrow();
        WorkoutSession session = workoutService.getOrCreateTodaySession(currentUser.id(), user.getTimezone());
        return ApiResponse.of(workoutService.toDto(session));
    }

    /** The caller's own Monday-Sunday week, already shifted for any skips. */
    @GetMapping("/week")
    public ApiResponse<List<WeekDayDto>> week() {
        User user = userRepository.findById(currentUser.id()).orElseThrow();
        return ApiResponse.of(workoutService.weekSchedule(currentUser.id(), user.getTimezone()));
    }

    @PostMapping("/today/skip")
    public ApiResponse<WorkoutSessionDto> skipToday(@Valid @RequestBody SkipWorkoutRequest request) {
        User user = userRepository.findById(currentUser.id()).orElseThrow();
        WorkoutSession session = workoutService.skipToday(currentUser.id(), user.getTimezone(), request.reason());
        return ApiResponse.of(workoutService.toDto(session));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkoutSessionDto>> create(@RequestBody(required = false) CreateWorkoutRequest request) {
        UUID programDayId = request == null ? null : request.programDayId();
        WorkoutSession session = workoutService.startWorkout(currentUser.id(), programDayId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(workoutService.toDto(session)));
    }

    @GetMapping
    public ApiResponse<PageResponse<WorkoutSummaryDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UUID resolvedClientId = authorizationService.resolveClientId(clientId);
        Page<WorkoutSession> result = workoutService.history(resolvedClientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "startedAt")));
        return ApiResponse.of(PageResponse.from(result.map(workoutService::toSummaryDto)));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkoutSessionDto> get(@PathVariable UUID id) {
        return ApiResponse.of(workoutService.toDto(workoutService.getById(id)));
    }

    @PostMapping("/{id}/sets")
    public ResponseEntity<ApiResponse<SetCompletionResult>> logSet(@PathVariable UUID id,
                                                                    @Valid @RequestBody LogSetRequest request) {
        SetCompletionResult result = workoutService.logSet(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(result));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<WorkoutSessionDto> complete(@PathVariable UUID id,
                                                    @Valid @RequestBody CompleteWorkoutRequest request) {
        return ApiResponse.of(workoutService.completeWorkout(id, request));
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<ApiResponse<WorkoutFeedbackDto>> leaveFeedback(@PathVariable UUID id,
                                                                          @Valid @RequestBody CreateFeedbackRequest request) {
        WorkoutFeedbackDto feedback = workoutService.leaveFeedback(id, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(feedback));
    }
}
