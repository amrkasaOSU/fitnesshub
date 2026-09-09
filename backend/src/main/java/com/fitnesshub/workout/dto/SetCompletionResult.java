package com.fitnesshub.workout.dto;

import com.fitnesshub.progress.PersonalRecord;

import java.util.List;

public record SetCompletionResult(
        WorkoutSetDto set,
        List<PersonalRecordDto> newPersonalRecords
) {
    public record PersonalRecordDto(String recordType, String value) {
        public static PersonalRecordDto from(PersonalRecord pr) {
            return new PersonalRecordDto(pr.getRecordType().name(), pr.getValue().toPlainString());
        }
    }
}
