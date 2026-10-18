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
            return invalid(null, null, null, null, "Model output was not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return invalid(null, null, null, null, "Model output must be a JSON object");
        }

        String company = limitedString(root.path("company"));
        String jobTitle = limitedString(root.path("jobTitle"));
        String nextAction = limitedString(root.path("nextAction"));
        EmailCategory category = null;
        String categoryValue = limitedString(root.path("category"));
        if (categoryValue != null) {
            try {
                category = EmailCategory.valueOf(categoryValue);
            } catch (IllegalArgumentException ignored) {
                return invalid(company, jobTitle, nextAction, confidence(root.path("confidence")),
                        "Model returned an unknown category");
            }
        }
        Double confidence = confidence(root.path("confidence"));
        JsonNode reviewNode = root.path("requiresReview");
        boolean requiresReview = reviewNode.isBoolean() && reviewNode.booleanValue();

        if (category == null) {
            return invalid(company, jobTitle, nextAction, confidence, "Model category is missing");
        }
        if (confidence == null || confidence < 0 || confidence > 1) {
            return invalid(company, jobTitle, nextAction, null, "Model confidence must be between 0 and 1");
        }
        if (!reviewNode.isBoolean()) {
            return invalid(company, jobTitle, nextAction, confidence, "Model requiresReview must be boolean");
        }
        if (nextAction == null) {
            return invalid(company, jobTitle, null, confidence, "Model nextAction must be a string");
        }
        if (category != EmailCategory.UNRELATED && (company == null || company.isBlank())) {
            return invalid(null, jobTitle, nextAction, confidence, "Model company is required");
        }
        return new ClassificationResult(category, company, jobTitle, confidence,
                nextAction, requiresReview, true, null);
    }

    private static ClassificationResult invalid(
            String company, String jobTitle, String nextAction, Double confidence, String error) {
        return new ClassificationResult(null, company, jobTitle, confidence,
                nextAction, true, false, error);
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
