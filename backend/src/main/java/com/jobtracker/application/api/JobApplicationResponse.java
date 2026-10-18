package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record JobApplicationResponse(
        Long id,
        String company,
        String jobTitle,
        String jobUrl,
        LocalDate dateApplied,
        ApplicationStatus status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}