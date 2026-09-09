package com.fitnesshub.goal;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.goal.dto.CheckInDto;
import com.fitnesshub.goal.dto.ReviewCheckInRequest;
import com.fitnesshub.goal.dto.SubmitCheckInRequest;
import com.fitnesshub.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CheckInController {

    private final CheckInService checkInService;
    private final CurrentUser currentUser;

    public CheckInController(CheckInService checkInService, CurrentUser currentUser) {
        this.checkInService = checkInService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/checkins")
    public ResponseEntity<ApiResponse<CheckInDto>> submit(@Valid @RequestBody SubmitCheckInRequest request) {
        CheckIn checkIn = checkInService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(checkInService.toDto(checkIn)));
    }

    @GetMapping("/api/checkins")
    public ApiResponse<PageResponse<CheckInDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<CheckIn> result = checkInService.history(clientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "weekStartDate")));
        return ApiResponse.of(PageResponse.from(result.map(checkInService::toDto)));
    }

    @PatchMapping("/api/checkins/{id}/review")
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<CheckInDto> review(@PathVariable UUID id, @Valid @RequestBody ReviewCheckInRequest request) {
        return ApiResponse.of(checkInService.toDto(checkInService.review(id, request)));
    }

    @GetMapping("/api/coach/checkins")
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<List<CheckInDto>> pendingReview() {
        return ApiResponse.of(checkInService.pendingReviewForCoach(currentUser.id()).stream()
                .map(checkInService::toDto).toList());
    }
}
