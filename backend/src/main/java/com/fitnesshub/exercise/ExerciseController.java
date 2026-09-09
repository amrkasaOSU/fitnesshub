package com.fitnesshub.exercise;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.exercise.dto.CreateExerciseRequest;
import com.fitnesshub.exercise.dto.ExerciseDto;
import com.fitnesshub.progress.ExerciseProgressService;
import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseProgressService exerciseProgressService;

    public ExerciseController(ExerciseService exerciseService, ExerciseMapper exerciseMapper,
                               ExerciseProgressService exerciseProgressService) {
        this.exerciseService = exerciseService;
        this.exerciseMapper = exerciseMapper;
        this.exerciseProgressService = exerciseProgressService;
    }

    @GetMapping("/api/exercises")
    public ApiResponse<PageResponse<ExerciseDto>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MuscleGroup muscleGroup,
            @RequestParam(required = false) Equipment equipment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<Exercise> result = exerciseService.search(query, muscleGroup, equipment,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by("name")));
        return ApiResponse.of(PageResponse.from(result.map(exerciseMapper::toDto)));
    }

    @GetMapping("/api/exercises/{id}")
    public ApiResponse<ExerciseDto> get(@PathVariable UUID id) {
        return ApiResponse.of(exerciseMapper.toDto(exerciseService.getById(id)));
    }

    @GetMapping("/api/exercises/{id}/progress")
    public ApiResponse<ExerciseProgressSummary> progress(@PathVariable UUID id,
                                                          @RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(exerciseProgressService.getProgress(clientId, id));
    }

    @PostMapping("/api/coach/exercises")
    public ResponseEntity<ApiResponse<ExerciseDto>> create(@Valid @RequestBody CreateExerciseRequest request) {
        Exercise created = exerciseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(exerciseMapper.toDto(created)));
    }
}
