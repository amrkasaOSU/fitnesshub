package com.fitnesshub.bodyweight;

import com.fitnesshub.bodyweight.dto.LogWeightRequest;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.bodyweight.dto.WeightEntryDto;
import com.fitnesshub.bodyweight.dto.WeightTrendPointDto;
import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/weight")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WeightEntryDto>> log(@Valid @RequestBody LogWeightRequest request) {
        WeightEntry entry = weightService.log(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toDto(entry)));
    }

    @GetMapping
    public ApiResponse<PageResponse<WeightEntryDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<WeightEntry> result = weightService.history(clientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "recordedAt")));
        return ApiResponse.of(PageResponse.from(result.map(this::toDto)));
    }

    @GetMapping("/dashboard")
    public ApiResponse<WeightDashboardDto> dashboard(@RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(weightService.dashboard(clientId));
    }

    @GetMapping("/trend")
    public ApiResponse<java.util.List<WeightTrendPointDto>> trend(
            @RequestParam(required = false) UUID clientId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ApiResponse.of(weightService.trend(clientId, from, to));
    }

    private WeightEntryDto toDto(WeightEntry e) {
        return new WeightEntryDto(e.getId(), e.getWeight(), e.getRecordedAt(), e.getNotes());
    }
}
