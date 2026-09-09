package com.fitnesshub.notification;

import com.fitnesshub.common.exception.ForbiddenException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationPreferenceRepository preferenceRepository,
                                CurrentUser currentUser) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.currentUser = currentUser;
    }

    /**
     * Creates a notification unless the user has opted out of this type, or an
     * identical one (same user/type/related entity) already exists - the
     * dedupe guard the spec calls for so retries or double PR checks never spam.
     */
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body,
                        String relatedType, UUID relatedId) {
        if (!isEnabled(userId, type)) {
            return;
        }
        if (relatedId != null && notificationRepository
                .existsByUserIdAndRelatedTypeAndRelatedIdAndType(userId, relatedType, relatedId, type)) {
            return;
        }
        notificationRepository.save(new Notification(userId, type, title, body, relatedType, relatedId));
    }

    private boolean isEnabled(UUID userId, NotificationType type) {
        NotificationPreference prefs = preferenceRepository.findByUserId(userId).orElse(null);
        if (prefs == null) {
            return true;
        }
        Predicate<NotificationPreference> check = switch (type) {
            case WORKOUT_REMINDER -> NotificationPreference::isWorkoutReminders;
            case NEW_MESSAGE -> NotificationPreference::isMessageNotifications;
            case PR_ACHIEVED -> NotificationPreference::isPrNotifications;
            case CHECKIN_REMINDER -> NotificationPreference::isCheckinReminders;
            case COACH_FEEDBACK -> NotificationPreference::isCoachFeedbackNotifications;
            case GOAL_MILESTONE -> NotificationPreference::isGoalMilestoneNotifications;
            case SUBSCRIPTION -> p -> true;
        };
        return check.test(prefs);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id(), pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUser.id());
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (!notification.getUserId().equals(currentUser.id())) {
            throw ForbiddenException.accessDenied();
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllRead() {
        Instant now = Instant.now();
        for (Notification n : notificationRepository.findByUserIdAndReadAtIsNull(currentUser.id())) {
            n.setReadAt(now);
            notificationRepository.save(n);
        }
    }
}
