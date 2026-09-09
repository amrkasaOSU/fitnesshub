package com.fitnesshub.messaging;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.common.exception.ForbiddenException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.messaging.dto.ConversationDto;
import com.fitnesshub.messaging.dto.SendMessageRequest;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.notification.NotificationType;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public MessagingService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                             ClientProfileRepository clientProfileRepository, UserRepository userRepository,
                             CurrentUser currentUser, AuthorizationService authorizationService,
                             NotificationService notificationService, AuditService auditService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** Resolves (creating if necessary) the conversation the caller means, given an optional clientId. */
    private Conversation resolveConversation(UUID requestedClientId, boolean createIfMissing) {
        UUID coachId;
        UUID clientId;
        if (currentUser.isClient()) {
            clientId = currentUser.id();
            ClientProfile profile = clientProfileRepository.findByUserId(clientId)
                    .orElseThrow(() -> NotFoundException.of("ClientProfile", clientId));
            coachId = profile.getCoachId();
        } else if (currentUser.isCoach()) {
            if (requestedClientId == null) {
                throw new IllegalArgumentException("clientId is required.");
            }
            authorizationService.assertCoachOwnsClient(currentUser.id(), requestedClientId);
            coachId = currentUser.id();
            clientId = requestedClientId;
        } else {
            throw ForbiddenException.accessDenied();
        }

        Optional<Conversation> existing = conversationRepository.findByCoachIdAndClientId(coachId, clientId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!createIfMissing) {
            return null;
        }
        return conversationRepository.save(new Conversation(coachId, clientId));
    }

    @Transactional
    public Message send(SendMessageRequest request) {
        Conversation conversation = resolveConversation(request.clientId(), true);
        Message message = messageRepository.save(new Message(conversation.getId(), currentUser.id(), request.content()));

        UUID recipientId = currentUser.id().equals(conversation.getCoachId())
                ? conversation.getClientId() : conversation.getCoachId();
        User sender = userRepository.findById(currentUser.id()).orElse(null);
        String senderName = sender == null ? "Your coach" : sender.getFirstName();
        notificationService.notify(recipientId, NotificationType.NEW_MESSAGE, "New message from " + senderName,
                truncate(request.content()), "Message", message.getId());

        auditService.record(currentUser.id(), AuditAction.MESSAGE_SENT, "Message", message.getId());
        return message;
    }

    @Transactional(readOnly = true)
    public Page<Message> history(UUID requestedClientId, Pageable pageable) {
        Conversation conversation = resolveConversation(requestedClientId, false);
        if (conversation == null) {
            return Page.empty(pageable);
        }
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId(), pageable);
    }

    @Transactional
    public void markRead(UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> NotFoundException.of("Message", messageId));
        Conversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> NotFoundException.of("Conversation", message.getConversationId()));

        boolean isParticipant = conversation.getCoachId().equals(currentUser.id())
                || conversation.getClientId().equals(currentUser.id());
        if (!isParticipant) {
            throw ForbiddenException.accessDenied();
        }
        if (message.getSenderId().equals(currentUser.id())) {
            return; // marking your own sent message as read is a no-op, not an error
        }
        if (message.getReadAt() == null) {
            message.setReadAt(Instant.now());
            messageRepository.save(message);
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listConversationsForCoach(UUID coachId) {
        return conversationRepository.findByCoachId(coachId).stream().map(conv -> {
            User client = userRepository.findById(conv.getClientId()).orElse(null);
            List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conv.getId());
            Message last = messages.isEmpty() ? null : messages.get(0);
            long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conv.getId(), coachId);
            return new ConversationDto(conv.getId(), conv.getCoachId(), conv.getClientId(),
                    client == null ? "Client" : client.getFirstName() + " " + client.getLastName(),
                    last == null ? null : truncate(last.getContent()),
                    last == null ? null : last.getCreatedAt(), unread);
        }).toList();
    }

    private String truncate(String content) {
        return content.length() <= 120 ? content : content.substring(0, 117) + "...";
    }
}
