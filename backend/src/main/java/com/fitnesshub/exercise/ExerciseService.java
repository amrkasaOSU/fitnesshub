package com.fitnesshub.exercise;

import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.exercise.dto.CreateExerciseRequest;
import com.fitnesshub.exercise.dto.ExerciseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseCatalog exerciseCatalog;

    public ExerciseService(ExerciseRepository exerciseRepository, ExerciseCatalog exerciseCatalog) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseCatalog = exerciseCatalog;
    }

    /**
     * One page of the library, served from the cached catalog rather than the
     * database. Filter precedence matches {@link #search}: a name query wins,
     * then muscle group, then equipment. The catalog is already in name order,
     * so paging is a slice.
     */
    public PageResponse<ExerciseDto> searchCatalog(String query, MuscleGroup muscleGroup, Equipment equipment,
                                                   int page, int pageSize) {
        List<ExerciseDto> matches = exerciseCatalog.all();
        if (query != null && !query.isBlank()) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            matches = matches.stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
        } else if (muscleGroup != null) {
            matches = matches.stream().filter(e -> e.muscleGroup() == muscleGroup).toList();
        } else if (equipment != null) {
            matches = matches.stream().filter(e -> e.equipment() == equipment).toList();
        }
        int from = (int) Math.min((long) page * pageSize, matches.size());
        int to = Math.min(from + pageSize, matches.size());
        int totalPages = pageSize == 0 ? 1 : (int) Math.ceil(matches.size() / (double) pageSize);
        return new PageResponse<>(matches.subList(from, to), page, pageSize, totalPages, matches.size());
    }

    @Transactional(readOnly = true)
    public Page<Exercise> search(String query, MuscleGroup muscleGroup, Equipment equipment, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return exerciseRepository.findByNameContainingIgnoreCase(query.trim(), pageable);
        }
        if (muscleGroup != null) {
            return exerciseRepository.findByMuscleGroup(muscleGroup, pageable);
        }
        if (equipment != null) {
            return exerciseRepository.findByEquipment(equipment, pageable);
        }
        return exerciseRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Exercise getById(UUID id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Exercise", id));
    }

    @Transactional
    public Exercise create(CreateExerciseRequest request) {
        Exercise exercise = new Exercise(request.name(), request.muscleGroup(), request.equipment(), request.movementPattern());
        exercise.setDescription(request.description());
        exercise.setInstructions(request.instructions());
        exercise.setVideoUrl(request.videoUrl());
        exercise.setImageUrl(request.imageUrl());
        exercise.setSystemExercise(false);
        Exercise saved = exerciseRepository.save(exercise);
        exerciseCatalog.evict();
        return saved;
    }
}
