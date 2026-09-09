package com.fitnesshub.program;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.program.dto.AssignProgramRequest;
import com.fitnesshub.program.dto.ProgramDto;
import com.fitnesshub.program.dto.ProgramRequest;
import com.fitnesshub.program.dto.ProgramSummaryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PreAuthorize("hasRole('COACH')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProgramDto>> create(@Valid @RequestBody ProgramRequest request) {
        Program program = programService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(programService.toFullDto(program)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProgramSummaryDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<Program> result = programService.listForCurrentCoach(PageRequest.of(page, Math.min(pageSize, 100)));
        return ApiResponse.of(PageResponse.from(result.map(programService::toSummaryDto)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProgramDto> get(@PathVariable UUID id) {
        return ApiResponse.of(programService.toFullDto(programService.getOwned(id)));
    }

    @PreAuthorize("hasRole('COACH')")
    @PatchMapping("/{id}")
    public ApiResponse<ProgramDto> update(@PathVariable UUID id, @Valid @RequestBody ProgramRequest request) {
        Program updated = programService.update(id, request);
        return ApiResponse.of(programService.toFullDto(updated));
    }

    @PreAuthorize("hasRole('COACH')")
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<Void>> assign(@PathVariable UUID id, @Valid @RequestBody AssignProgramRequest request) {
        programService.assign(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(null));
    }
}
