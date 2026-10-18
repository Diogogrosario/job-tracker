package com.jobtracker.email.review;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.email.processing.EmailCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReviewQueueResponse(
        Long id,
        Long emailMessageId,
        ReviewAction action,
        ApplicationStatus proposedStatus,
        String proposedCompany,
        String proposedJobTitle,
        EmailCategory category,
        BigDecimal confidence,
        String reason,
        ReviewStatus status,
        OffsetDateTime createdAt,
        String subject,
        String fromAddress,
        OffsetDateTime receivedAt,
        String snippet,
        Long applicationId,
        String applicationCompany,
        String applicationJobTitle,
        ApplicationStatus applicationStatus) {
}
