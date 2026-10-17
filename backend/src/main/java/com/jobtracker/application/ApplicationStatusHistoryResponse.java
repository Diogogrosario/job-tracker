package com.jobtracker.application;

import java.time.OffsetDateTime;

public record ApplicationStatusHistoryResponse(
        Long id,
        ApplicationStatus oldStatus,
        ApplicationStatus newStatus,
        String source,
        String sourceMessageId,
        OffsetDateTime changedAt) {
}