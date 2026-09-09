package com.fitnesshub.goal;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.goal.dto.CreateGoalRequest;
import com.fitnesshub.goal.dto.GoalDto;
import com.fitnesshub.goal.dto.UpdateGoalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalDto>> create(@Valid @RequestBody CreateGoalRequest request) {
        Goal goal = goalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(goalService.toDto(goal)));
    }

    @GetMapping
    public ApiResponse<List<GoalDto>> list(@RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(goalService.listForClient(clientId).stream().map(goalService::toDto).toList());
    }

    @PatchMapping("/{id}")
    public ApiResponse<GoalDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest request) {
        return ApiResponse.of(goalService.toDto(goalService.update(id, request)));
    }
}
