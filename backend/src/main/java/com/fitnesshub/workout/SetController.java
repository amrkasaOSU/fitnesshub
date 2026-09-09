package com.fitnesshub.workout;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.workout.dto.UpdateSetRequest;
import com.fitnesshub.workout.dto.WorkoutSetDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sets")
public class SetController {

    private final WorkoutService workoutService;

    public SetController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PatchMapping("/{id}")
    public ApiResponse<WorkoutSetDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateSetRequest request) {
        return ApiResponse.of(workoutService.updateSet(id, request));
    }
}
