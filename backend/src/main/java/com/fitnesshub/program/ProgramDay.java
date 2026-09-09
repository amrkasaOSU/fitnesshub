package com.fitnesshub.program;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "program_days")
public class ProgramDay extends BaseEntity {

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    protected ProgramDay() {
    }

    public ProgramDay(UUID programId, Integer dayNumber, String name) {
        this.programId = programId;
        this.dayNumber = dayNumber;
        this.name = name;
    }

    public UUID getProgramId() {
        return programId;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
