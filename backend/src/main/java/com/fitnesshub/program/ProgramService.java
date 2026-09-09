package com.fitnesshub.program;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.ForbiddenException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.exercise.Exercise;
import com.fitnesshub.exercise.ExerciseRepository;
import com.fitnesshub.program.dto.AssignProgramRequest;
import com.fitnesshub.program.dto.ProgramDayDto;
import com.fitnesshub.program.dto.ProgramDayRequest;
import com.fitnesshub.program.dto.ProgramDto;
import com.fitnesshub.program.dto.ProgramExerciseDto;
import com.fitnesshub.program.dto.ProgramExerciseRequest;
import com.fitnesshub.program.dto.ProgramRequest;
import com.fitnesshub.program.dto.ProgramSummaryDto;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final ClientProgramRepository clientProgramRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public ProgramService(ProgramRepository programRepository,
                           ProgramDayRepository programDayRepository,
                           ProgramExerciseRepository programExerciseRepository,
                           ClientProgramRepository clientProgramRepository,
                           ExerciseRepository exerciseRepository,
                           CurrentUser currentUser,
                           AuthorizationService authorizationService,
                           AuditService auditService) {
        this.programRepository = programRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.clientProgramRepository = clientProgramRepository;
        this.exerciseRepository = exerciseRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public Program create(ProgramRequest request) {
        UUID coachId = currentUser.id();
        Program program = new Program(coachId, request.name(), request.durationWeeks(), request.goal());
        program.setDescription(request.description());
        program = programRepository.save(program);
        writeDays(program.getId(), request.days());
        auditService.record(coachId, AuditAction.PROGRAM_CREATED, "Program", program.getId());
        return program;
    }

    @Transactional(readOnly = true)
    public Page<Program> listForCurrentCoach(Pageable pageable) {
        return programRepository.findByCoachId(currentUser.id(), pageable);
    }

    @Transactional(readOnly = true)
    public Program getOwned(UUID programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> NotFoundException.of("Program", programId));
        if (currentUser.isAdmin()) {
            return program;
        }
        if (currentUser.isCoach()) {
            if (!program.getCoachId().equals(currentUser.id())) {
                throw ForbiddenException.accessDenied();
            }
            return program;
        }
        boolean assignedToMe = clientProgramRepository.findByProgramId(programId).stream()
                .anyMatch(cp -> cp.getClientId().equals(currentUser.id()));
        if (!assignedToMe) {
            throw ForbiddenException.accessDenied();
        }
        return program;
    }

    @Transactional(readOnly = true)
    public ProgramDto toFullDto(Program program) {
        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId());
        List<UUID> dayIds = days.stream().map(ProgramDay::getId).toList();
        List<ProgramExercise> allExercises = dayIds.isEmpty()
                ? List.of()
                : programExerciseRepository.findByProgramDayIdInOrderByOrderIndexAsc(dayIds);

        Map<UUID, String> exerciseNames = exerciseRepository.findAllById(
                        allExercises.stream().map(ProgramExercise::getExerciseId).distinct().toList())
                .stream().collect(Collectors.toMap(Exercise::getId, Exercise::getName));

        Map<UUID, List<ProgramExercise>> byDay = allExercises.stream()
                .collect(Collectors.groupingBy(ProgramExercise::getProgramDayId));

        List<ProgramDayDto> dayDtos = days.stream().map(day -> new ProgramDayDto(
                day.getId(), day.getDayNumber(), day.getName(), day.getDescription(),
                byDay.getOrDefault(day.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(ProgramExercise::getOrderIndex))
                        .map(pe -> new ProgramExerciseDto(
                                pe.getId(), pe.getExerciseId(), exerciseNames.get(pe.getExerciseId()),
                                pe.getOrderIndex(), pe.getSets(), pe.getTargetReps(), pe.getTargetWeight(),
                                pe.getTargetRpe(), pe.getRestSeconds(), pe.getTempo(), pe.getNotes(),
                                pe.getProgressionStrategy()))
                        .toList()
        )).toList();

        return new ProgramDto(program.getId(), program.getCoachId(), program.getName(), program.getDescription(),
                program.getDurationWeeks(), program.getGoal(), program.getVersion(), dayDtos);
    }

    public ProgramSummaryDto toSummaryDto(Program program) {
        int dayCount = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId()).size();
        int assigned = clientProgramRepository.findByProgramId(program.getId()).size();
        return new ProgramSummaryDto(program.getId(), program.getName(), program.getDurationWeeks(),
                program.getGoal(), program.getVersion(), dayCount, assigned);
    }

    /**
     * If the program has never been assigned to a client, edits happen in place.
     * If it has, we freeze the existing rows (they stay attached to whatever
     * WorkoutSession/ClientProgram history already points at them) and create a
     * new Program version carrying the requested changes.
     */
    @Transactional
    public Program update(UUID programId, ProgramRequest request) {
        Program existing = getOwned(programId);
        boolean hasAssignments = !clientProgramRepository.findByProgramId(programId).isEmpty();

        Program target;
        if (hasAssignments) {
            target = new Program(existing.getCoachId(), request.name(), request.durationWeeks(), request.goal());
            target.setDescription(request.description());
            target.setVersion(existing.getVersion() + 1);
            target.setPreviousVersionId(existing.getId());
            target = programRepository.save(target);
        } else {
            existing.setName(request.name());
            existing.setDescription(request.description());
            existing.setDurationWeeks(request.durationWeeks());
            existing.setGoal(request.goal());
            target = existing;
            for (ProgramDay day : programDayRepository.findByProgramIdOrderByDayNumberAsc(target.getId())) {
                programExerciseRepository.deleteAll(
                        programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId()));
            }
            programDayRepository.deleteAll(programDayRepository.findByProgramIdOrderByDayNumberAsc(target.getId()));
        }

        writeDays(target.getId(), request.days());
        auditService.record(currentUser.id(), AuditAction.PROGRAM_UPDATED, "Program", target.getId());
        return target;
    }

    private void writeDays(UUID programId, List<ProgramDayRequest> dayRequests) {
        if (dayRequests == null) {
            return;
        }
        for (ProgramDayRequest dayRequest : dayRequests) {
            ProgramDay day = programDayRepository.save(
                    new ProgramDay(programId, dayRequest.dayNumber(), dayRequest.name()));
            day.setDescription(dayRequest.description());
            programDayRepository.save(day);

            if (dayRequest.exercises() == null) {
                continue;
            }
            for (ProgramExerciseRequest exerciseRequest : dayRequest.exercises()) {
                exerciseRepository.findById(exerciseRequest.exerciseId())
                        .orElseThrow(() -> NotFoundException.of("Exercise", exerciseRequest.exerciseId()));
                ProgramExercise pe = new ProgramExercise(day.getId(), exerciseRequest.exerciseId(),
                        exerciseRequest.orderIndex(), exerciseRequest.sets(), exerciseRequest.targetReps());
                pe.setTargetWeight(exerciseRequest.targetWeight());
                pe.setTargetRpe(exerciseRequest.targetRpe());
                pe.setRestSeconds(exerciseRequest.restSeconds());
                pe.setTempo(exerciseRequest.tempo());
                pe.setNotes(exerciseRequest.notes());
                if (exerciseRequest.progressionStrategy() != null) {
                    pe.setProgressionStrategy(exerciseRequest.progressionStrategy());
                }
                programExerciseRepository.save(pe);
            }
        }
    }

    @Transactional
    public ClientProgram assign(UUID programId, AssignProgramRequest request) {
        Program program = getOwned(programId);
        authorizationService.assertCoachOwnsClient(currentUser.id(), request.clientId());

        clientProgramRepository.findFirstByClientIdAndStatusOrderByStartDateDesc(request.clientId(), ClientProgramStatus.ACTIVE)
                .ifPresent(active -> {
                    active.setStatus(ClientProgramStatus.CANCELLED);
                    active.setEndDate(LocalDate.now());
                    clientProgramRepository.save(active);
                });

        if (request.startDate().isBefore(LocalDate.now().minusYears(1))) {
            throw new ConflictException("Start date is unreasonably far in the past.");
        }

        ClientProgram assignment = clientProgramRepository.save(
                new ClientProgram(request.clientId(), program.getId(), request.startDate()));
        auditService.record(currentUser.id(), AuditAction.PROGRAM_ASSIGNED, "ClientProgram", assignment.getId());
        return assignment;
    }
}
