package com.fitnesshub.client;

import com.fitnesshub.analytics.AnalyticsService;
import com.fitnesshub.analytics.dto.TrainingSummaryDto;
import com.fitnesshub.client.dto.ClientDetailDto;
import com.fitnesshub.client.dto.UpdateClientProfileRequest;
import com.fitnesshub.coach.CoachClient;
import com.fitnesshub.coach.CoachClientService;
import com.fitnesshub.coach.dto.ClientSummaryDto;
import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.goal.CheckInService;
import com.fitnesshub.goal.dto.CheckInDto;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.nutrition.NutritionService;
import com.fitnesshub.nutrition.dto.NutritionEntryDto;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.workout.WorkoutService;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.dto.WorkoutSummaryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final CoachClientService coachClientService;
    private final ClientProfileService clientProfileService;
    private final AnalyticsService analyticsService;
    private final WorkoutService workoutService;
    private final NutritionService nutritionService;
    private final CheckInService checkInService;
    private final CurrentUser currentUser;

    public ClientController(CoachClientService coachClientService, ClientProfileService clientProfileService,
                             AnalyticsService analyticsService, WorkoutService workoutService,
                             NutritionService nutritionService, CheckInService checkInService,
                             CurrentUser currentUser) {
        this.coachClientService = coachClientService;
        this.clientProfileService = clientProfileService;
        this.analyticsService = analyticsService;
        this.workoutService = workoutService;
        this.nutritionService = nutritionService;
        this.checkInService = checkInService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<List<ClientSummaryDto>> list() {
        List<CoachClient> active = coachClientService.activeClients(currentUser.id());
        return ApiResponse.of(active.stream().map(cc -> coachClientService.summarize(cc.getClientId())).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientDetailDto> get(@PathVariable UUID id) {
        return ApiResponse.of(clientProfileService.getDetail(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<ClientDetailDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateClientProfileRequest request) {
        return ApiResponse.of(clientProfileService.updateTargets(id, request));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<TrainingSummaryDto> progress(@PathVariable UUID id,
                                                      @RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(29) : from;
        return ApiResponse.of(analyticsService.trainingSummary(id, resolvedFrom, resolvedTo));
    }

    @GetMapping("/{id}/workouts")
    public ApiResponse<PageResponse<WorkoutSummaryDto>> workouts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<WorkoutSession> result = workoutService.history(id,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "startedAt")));
        return ApiResponse.of(PageResponse.from(result.map(workoutService::toSummaryDto)));
    }

    @GetMapping("/{id}/nutrition")
    public ApiResponse<PageResponse<NutritionEntryDto>> nutrition(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<NutritionEntry> result = nutritionService.history(id,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "date")));
        return ApiResponse.of(PageResponse.from(result.map(e -> new NutritionEntryDto(e.getId(), e.getDate(),
                e.getCalories(), e.getProteinGrams(), e.getCarbohydratesGrams(), e.getFatGrams(),
                e.getFiberGrams(), e.getWaterMl(), e.getNotes()))));
    }

    @GetMapping("/{id}/checkins")
    public ApiResponse<PageResponse<CheckInDto>> checkins(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<CheckIn> result = checkInService.history(id,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "weekStartDate")));
        return ApiResponse.of(PageResponse.from(result.map(checkInService::toDto)));
    }
}
