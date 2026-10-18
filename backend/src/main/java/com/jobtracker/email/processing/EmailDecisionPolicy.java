package com.jobtracker.email.processing;

import com.jobtracker.application.domain.ApplicationStatus;
import java.math.BigDecimal;
import java.util.Set;

public final class EmailDecisionPolicy {
    private static final BigDecimal RULE_AND_AI_AGREE = new BigDecimal("0.950");
    private static final BigDecimal RULE_ONLY = new BigDecimal("0.800");
    private static final BigDecimal AI_ONLY = new BigDecimal("0.500");

    private EmailDecisionPolicy() {
    }

    public static Decision decide(Set<EmailCategory> hints, ClassificationResult ai) {
        if (hints.size() == 1) {
            EmailCategory ruleCategory = hints.iterator().next();
            boolean agrees = ai.valid() && ai.category() == ruleCategory;
            return new Decision(
                    ruleCategory,
                    "RULE",
                    agrees ? RULE_AND_AI_AGREE : RULE_ONLY,
                    ai.valid() && ruleCategory != EmailCategory.UNRELATED,
                    agrees,
                    ai.valid() ? null : ai.validationError());
        }
        if (hints.size() > 1) {
            return new Decision(
                    ai.valid() ? ai.category() : null,
                    "AI",
                    AI_ONLY,
                    false,
                    false,
                    "Conflicting signals: " + String.join(" and ", hints.stream()
                            .map(EmailDecisionPolicy::categoryLabel)
                            .sorted()
                            .toList()));
        }
        if (!ai.valid() || ai.category() == null) {
            return new Decision(ai.category(), "AI", AI_ONLY, false, false,
                    ai.validationError() == null ? "Model output could not be parsed" : ai.validationError());
        }
        return new Decision(
                ai.category(),
                "AI",
                AI_ONLY,
                ai.category() != EmailCategory.UNRELATED,
                false,
                null);
    }

    public static boolean shouldAutoApply(
            Decision decision,
            boolean autoApplyMatched,
            boolean autoApplyAiOnly,
            boolean requireAgreement) {
        if (!autoApplyMatched || !decision.decided() || decision.category() == null) {
            return false;
        }
        if (requireAgreement && !decision.aiAgreesWithRule()) {
            return false;
        }
        if ("RULE".equals(decision.classifiedBy())) {
            return true;
        }
        return autoApplyAiOnly;
    }

    public static ApplicationStatus proposedStatus(Decision decision) {
        return decision.decided() ? suggestedStatus(decision) : null;
    }

    public static ApplicationStatus suggestedStatus(Decision decision) {
        return EmailProcessingRules.statusFor(decision.category());
    }

    public static String agreementReason(Set<EmailCategory> ruleHints, ClassificationResult classification) {
        if (!ruleHints.isEmpty()) {
            return classification.valid()
                    ? "Phrase match only; the model read it differently"
                    : "Phrase match only; model output was invalid";
        }
        return classification.valid() ? "Model suggestion only"
                : classification.validationError() == null
                        ? "Model output could not be parsed" : classification.validationError();
    }

    public static boolean shouldApplyStatus(ApplicationStatus current, ApplicationStatus target) {
        if (current == target) {
            return false;
        }
        if (current == ApplicationStatus.REJECTED || current == ApplicationStatus.CLOSED) {
            return false;
        }
        if (target == ApplicationStatus.REJECTED) {
            return true;
        }
        return EmailProcessingRules.isForwardOrUnchanged(current, target);
    }

    public static MatchAction matchedAction(
            Decision decision,
            ApplicationStatus current,
            boolean ambiguous,
            boolean autoApplyMatched,
            boolean autoApplyAiOnly,
            boolean requireAgreement) {
        if (ambiguous || !decision.decided() || decision.category() == null) {
            return MatchAction.ATTENTION;
        }
        ApplicationStatus target = EmailProcessingRules.statusFor(decision.category());
        if (target == null || current == target) {
            return MatchAction.LINK_ONLY;
        }
        if (current == ApplicationStatus.REJECTED || current == ApplicationStatus.CLOSED) {
            return MatchAction.ATTENTION;
        }
        if (target != ApplicationStatus.REJECTED
                && !EmailProcessingRules.isForwardOrUnchanged(current, target)) {
            return MatchAction.LINK_ONLY;
        }
        return shouldAutoApply(decision, autoApplyMatched, autoApplyAiOnly, requireAgreement)
                ? MatchAction.APPLY
                : MatchAction.ATTENTION;
    }

    private static String categoryLabel(EmailCategory category) {
        return category.name().toLowerCase().replace('_', ' ');
    }

    public record Decision(
            EmailCategory category,
            String classifiedBy,
            BigDecimal confidence,
            boolean decided,
            boolean aiAgreesWithRule,
            String reason) {
    }

    public enum MatchAction {
        APPLY,
        LINK_ONLY,
        ATTENTION
    }
}
