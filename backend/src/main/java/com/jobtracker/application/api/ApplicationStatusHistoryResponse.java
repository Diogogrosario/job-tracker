package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import java.time.OffsetDateTime;

public record ApplicationStatusHistoryResponse(
        Long id,
        ApplicationStatus oldStatus,
        ApplicationStatus newStatus,
        String source,
        String sourceMessageId,
        OffsetDateTime changedAt) {
}