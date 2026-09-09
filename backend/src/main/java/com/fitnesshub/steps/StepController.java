package com.fitnesshub.steps;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.steps.dto.LogStepsRequest;
import com.fitnesshub.steps.dto.StepDashboardDto;
import com.fitnesshub.steps.dto.StepEntryDto;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/steps")
public class StepController {

    private final StepService stepService;

    public StepController(StepService stepService) {
        this.stepService = stepService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StepEntryDto>> log(@Valid @RequestBody LogStepsRequest request) {
        StepEntry entry = stepService.log(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toDto(entry)));
    }

    @GetMapping
    public ApiResponse<PageResponse<StepEntryDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<StepEntry> result = stepService.history(clientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "date")));
        return ApiResponse.of(PageResponse.from(result.map(this::toDto)));
    }

    @GetMapping("/dashboard")
    public ApiResponse<StepDashboardDto> dashboard(@RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(stepService.dashboard(clientId));
    }

    private StepEntryDto toDto(StepEntry e) {
        return new StepEntryDto(e.getId(), e.getSteps(), e.getDate(), e.getSource());
    }
}
