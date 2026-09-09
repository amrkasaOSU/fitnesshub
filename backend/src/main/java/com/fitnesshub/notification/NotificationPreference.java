package com.fitnesshub.notification;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "workout_reminders", nullable = false)
    private boolean workoutReminders = true;

    @Column(name = "message_notifications", nullable = false)
    private boolean messageNotifications = true;

    @Column(name = "pr_notifications", nullable = false)
    private boolean prNotifications = true;

    @Column(name = "checkin_reminders", nullable = false)
    private boolean checkinReminders = true;

    @Column(name = "coach_feedback_notifications", nullable = false)
    private boolean coachFeedbackNotifications = true;

    @Column(name = "goal_milestone_notifications", nullable = false)
    private boolean goalMilestoneNotifications = true;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isWorkoutReminders() {
        return workoutReminders;
    }

    public void setWorkoutReminders(boolean workoutReminders) {
        this.workoutReminders = workoutReminders;
    }

    public boolean isMessageNotifications() {
        return messageNotifications;
    }

    public void setMessageNotifications(boolean messageNotifications) {
        this.messageNotifications = messageNotifications;
    }

    public boolean isPrNotifications() {
        return prNotifications;
    }

    public void setPrNotifications(boolean prNotifications) {
        this.prNotifications = prNotifications;
    }

    public boolean isCheckinReminders() {
        return checkinReminders;
    }

    public void setCheckinReminders(boolean checkinReminders) {
        this.checkinReminders = checkinReminders;
    }

    public boolean isCoachFeedbackNotifications() {
        return coachFeedbackNotifications;
    }

    public void setCoachFeedbackNotifications(boolean coachFeedbackNotifications) {
        this.coachFeedbackNotifications = coachFeedbackNotifications;
    }

    public boolean isGoalMilestoneNotifications() {
        return goalMilestoneNotifications;
    }

    public void setGoalMilestoneNotifications(boolean goalMilestoneNotifications) {
        this.goalMilestoneNotifications = goalMilestoneNotifications;
    }
}
