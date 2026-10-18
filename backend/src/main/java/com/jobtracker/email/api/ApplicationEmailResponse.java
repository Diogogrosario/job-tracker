package com.jobtracker.email.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ApplicationEmailResponse(
        Long id,
        String fromAddress,
        String subject,
        OffsetDateTime receivedAt,
        String category,
        BigDecimal confidence) {
}
