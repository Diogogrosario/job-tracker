package com.jobtracker.email.processing;

import com.jobtracker.application.domain.ApplicationStatus;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EmailProcessingRules {
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(?:inc|ltd|llc|gmbh|sa|lda|ltda|plc|corp|co)\\.?$");
    private static final Set<String> NOISE_SUBJECTS = Set.of(
            "job alert", "jobs for you", "recommended jobs", "new jobs", "weekly digest");

    private EmailProcessingRules() {
    }

    public static boolean isNoise(String subject) {
        String normalized = normalizeText(subject);
        return NOISE_SUBJECTS.stream().anyMatch(normalized::contains);
    }

    public static Set<EmailCategory> ruleHints(String subject, String body) {
        String text = normalizeText((subject == null ? "" : subject) + " " + (body == null ? "" : body));
        Set<EmailCategory> hints = EnumSet.noneOf(EmailCategory.class);
        if (containsAny(text, "thank you for applying", "we received your application", "your application to")) {
            hints.add(EmailCategory.APPLICATION_CONFIRMATION);
        }
        if (containsAny(text, "unfortunately", "not moving forward",
                "decided to proceed with other candidates", "regret to inform")) {
            hints.add(EmailCategory.REJECTION);
        }
        if (containsAny(text, "invite you to interview", "schedule an interview", "interview invitation")) {
            hints.add(EmailCategory.INTERVIEW_INVITATION);
        }
        if (containsAny(text, "online assessment", "coding challenge", "take home")) {
            hints.add(EmailCategory.ASSESSMENT);
        }
        if (containsAny(text, "pleased to offer", "offer of employment")) {
            hints.add(EmailCategory.OFFER);
        }
        return Set.copyOf(hints);
    }

    public static String normalizeCompany(String company) {
        String normalized = normalizeText(company).replaceAll("[^\\p{Alnum} ]", " ").trim();
        normalized = normalized.replaceAll("\\s+", " ");
        String previous;
        do {
            previous = normalized;
            normalized = LEGAL_SUFFIX.matcher(normalized).replaceFirst("").trim();
        } while (!normalized.equals(previous));
        return normalized.replaceAll("\\s+", " ");
    }

    public static double titleTokenOverlap(String first, String second) {
        Set<String> firstTokens = titleTokens(first);
        Set<String> secondTokens = titleTokens(second);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return 0;
        }
        long common = firstTokens.stream().filter(secondTokens::contains).count();
        return (double) common / (firstTokens.size() + secondTokens.size() - common);
    }

    public static ApplicationStatus statusFor(EmailCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case APPLICATION_CONFIRMATION -> ApplicationStatus.APPLIED;
            case INTERVIEW_INVITATION, ASSESSMENT -> ApplicationStatus.INTERVIEW;
            case OFFER -> ApplicationStatus.OFFER;
            case REJECTION -> ApplicationStatus.REJECTED;
            case UNRELATED -> null;
        };
    }

    public static boolean isForwardOrUnchanged(ApplicationStatus current, ApplicationStatus target) {
        return rank(target) >= rank(current);
    }

    public static boolean requiresStatusReview(ApplicationStatus current, ApplicationStatus target) {
        return current == ApplicationStatus.REJECTED
                || current == ApplicationStatus.CLOSED
                || target == ApplicationStatus.REJECTED;
    }

    private static int rank(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> 0;
            case INTERVIEW -> 1;
            case OFFER -> 2;
            case REJECTED, CLOSED -> -1;
        };
    }

    private static Set<String> titleTokens(String title) {
        return Arrays.stream(normalizeText(title).split("\\s+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return NON_ALPHANUMERIC.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll(" ").trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String text, String... phrases) {
        return Arrays.stream(phrases).anyMatch(text::contains);
    }
}
