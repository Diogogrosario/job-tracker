package com.jobtracker.email.processing;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OllamaOutputValidator {
    private OllamaOutputValidator() {
    }

    public static ClassificationResult validate(String output, ObjectMapper objectMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(output);
        } catch (JacksonException exception) {
            return invalid(null, null, null, null, "Model output could not be parsed");
        }
        if (root == null || !root.isObject()) {
            return invalid(null, null, null, null, "Model output must be a JSON object");
        }

        String company = limitedString(root.path("company"));
        String jobTitle = limitedString(root.path("jobTitle"));
        EmailCategory category = null;
        String categoryValue = limitedString(root.path("category"));
        if (categoryValue != null) {
            try {
                category = EmailCategory.valueOf(categoryValue);
            } catch (IllegalArgumentException ignored) {
                return invalid(null, company, jobTitle, confidence(root.path("confidence")),
                        "Model returned an unknown category");
            }
        }
        Double confidence = confidence(root.path("confidence"));
        JsonNode reviewNode = root.path("requiresReview");
        boolean requiresReview = reviewNode.isBoolean() && reviewNode.booleanValue();

        if (category == null) {
            return invalid(null, company, jobTitle, confidence, "Model category is missing");
        }
        if (confidence == null || confidence < 0 || confidence > 1) {
            return invalid(category, company, jobTitle, null,
                    "Model confidence must be between 0 and 1");
        }
        if (!reviewNode.isBoolean()) {
            return invalid(category, company, jobTitle, confidence,
                    "Model requiresReview must be boolean");
        }
        if (category != EmailCategory.UNRELATED && (company == null || company.isBlank())) {
            return invalid(category, null, jobTitle, confidence, "Model company is required");
        }
        return new ClassificationResult(category, company, jobTitle, confidence,
                requiresReview, true, null);
    }

    private static ClassificationResult invalid(
            EmailCategory category, String company, String jobTitle, Double confidence, String error) {
        return new ClassificationResult(category, company, jobTitle, confidence,
                true, false, error);
    }

    private static String limitedString(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asString().trim();
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private static Double confidence(JsonNode node) {
        return node != null && node.isNumber() ? node.doubleValue() : null;
    }
}
