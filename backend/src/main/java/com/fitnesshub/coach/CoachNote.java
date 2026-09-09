package com.fitnesshub.coach;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Never exposed to the client this note is about - enforced in CoachNoteService, not just the UI. */
@Entity
@Table(name = "coach_notes")
public class CoachNote extends BaseEntity {

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected CoachNote() {
    }

    public CoachNote(UUID coachId, UUID clientId, String content) {
        this.coachId = coachId;
        this.clientId = clientId;
        this.content = content;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
