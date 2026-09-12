package com.fitnesshub.coach;

import com.fitnesshub.coach.dto.CoachNoteDto;
import com.fitnesshub.coach.dto.CreateClientRequest;
import com.fitnesshub.coach.dto.CreateNoteRequest;
import com.fitnesshub.coach.dto.ResetClientPasswordRequest;
import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserMapper;
import com.fitnesshub.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/coach")
@PreAuthorize("hasRole('COACH')")
public class CoachController {

    private final CoachClientService coachClientService;
    private final UserMapper userMapper;

    public CoachController(CoachClientService coachClientService, UserMapper userMapper) {
        this.coachClientService = coachClientService;
        this.userMapper = userMapper;
    }

    @PostMapping("/clients")
    public ResponseEntity<ApiResponse<UserDto>> createClient(@Valid @RequestBody CreateClientRequest request) {
        User client = coachClientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(userMapper.toDto(client)));
    }

    @PostMapping("/clients/{id}/password")
    public ResponseEntity<Void> resetClientPassword(@PathVariable UUID id,
                                                     @Valid @RequestBody ResetClientPasswordRequest request) {
        coachClientService.resetClientPassword(id, request.temporaryPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clients/{id}/notes")
    public ResponseEntity<ApiResponse<CoachNoteDto>> addNote(@PathVariable UUID id,
                                                              @Valid @RequestBody CreateNoteRequest request) {
        CoachNote note = coachClientService.addNote(id, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toDto(note)));
    }

    @GetMapping("/clients/{id}/notes")
    public ApiResponse<List<CoachNoteDto>> listNotes(@PathVariable UUID id) {
        return ApiResponse.of(coachClientService.listNotes(id).stream().map(this::toDto).toList());
    }

    private CoachNoteDto toDto(CoachNote note) {
        return new CoachNoteDto(note.getId(), note.getContent(), note.getCreatedAt());
    }
}
