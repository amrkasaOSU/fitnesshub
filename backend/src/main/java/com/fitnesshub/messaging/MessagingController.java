package com.fitnesshub.messaging;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.messaging.dto.ConversationDto;
import com.fitnesshub.messaging.dto.MessageDto;
import com.fitnesshub.messaging.dto.SendMessageRequest;
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
public class MessagingController {

    private final MessagingService messagingService;
    private final CurrentUser currentUser;

    public MessagingController(MessagingService messagingService, CurrentUser currentUser) {
        this.messagingService = messagingService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/messages")
    public ResponseEntity<ApiResponse<MessageDto>> send(@Valid @RequestBody SendMessageRequest request) {
        Message message = messagingService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toDto(message)));
    }

    @GetMapping("/api/messages")
    public ApiResponse<PageResponse<MessageDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<Message> result = messagingService.history(clientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.of(PageResponse.from(result.map(this::toDto)));
    }

    @PatchMapping("/api/messages/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        messagingService.markRead(id);
        return ApiResponse.of(null);
    }

    @GetMapping("/api/coach/conversations")
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<List<ConversationDto>> conversations() {
        return ApiResponse.of(messagingService.listConversationsForCoach(currentUser.id()));
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(), m.getContent(),
                m.getCreatedAt(), m.getReadAt());
    }
}
