package com.jobtracker.email.processing;

public record ClassificationResult(
        EmailCategory category,
        String company,
        String jobTitle,
        Double confidence,
        boolean requiresReview,
        boolean valid,
        String validationError) {
}
