package com.jobtracker.email.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jobtracker.application.domain.ApplicationStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmailDecisionPolicyTest {
    @Test
    void matchedRuleDecisionAppliesAutomatically() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.OFFER), classification(EmailCategory.OFFER, true));
        assertEquals(EmailDecisionPolicy.MatchAction.APPLY, matched(decision, ApplicationStatus.INTERVIEW));
        assertEquals("RULE", decision.classifiedBy());
        assertEquals("0.950", decision.confidence().toPlainString());
    }

    @Test
    void ruleRejectionAndAiUnrelatedRequireAttentionAndSuggestRejected() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.REJECTION), classification(EmailCategory.UNRELATED, true));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.OFFER));
        assertEquals(ApplicationStatus.REJECTED, EmailDecisionPolicy.suggestedStatus(decision));
        assertEquals("Phrase match only; the model read it differently",
                EmailDecisionPolicy.agreementReason(
                        Set.of(EmailCategory.REJECTION), classification(EmailCategory.UNRELATED, true)));
    }

    @Test
    void ruleRejectionAndInvalidAiOutputRequireAttentionAndSuggestRejected() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.REJECTION), classification(EmailCategory.UNRELATED, false));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.OFFER));
        assertEquals(ApplicationStatus.REJECTED, EmailDecisionPolicy.suggestedStatus(decision));
        assertEquals("Phrase match only; model output was invalid",
                EmailDecisionPolicy.agreementReason(
                        Set.of(EmailCategory.REJECTION), classification(EmailCategory.UNRELATED, false)));
    }

    @Test
    void aiOnlyInterviewSuggestionRequiresAttentionWhenDisabled() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(), classification(EmailCategory.INTERVIEW_INVITATION, true));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.APPLIED));
        assertEquals(ApplicationStatus.INTERVIEW, EmailDecisionPolicy.suggestedStatus(decision));
        assertEquals("Model suggestion only",
                EmailDecisionPolicy.agreementReason(Set.of(),
                        classification(EmailCategory.INTERVIEW_INVITATION, true)));
    }

    @Test
    void disablingAgreementRestoresRuleOnlyAutomaticUpdates() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.REJECTION), classification(EmailCategory.UNRELATED, true));
        assertEquals(EmailDecisionPolicy.MatchAction.APPLY,
                matched(decision, ApplicationStatus.OFFER, false, false));
    }

    @Test
    void sameStatusOnlyLinks() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.INTERVIEW_INVITATION),
                classification(EmailCategory.INTERVIEW_INVITATION, true));
        assertEquals(EmailDecisionPolicy.MatchAction.LINK_ONLY, matched(decision, ApplicationStatus.INTERVIEW));
    }

    @Test
    void backwardStatusOnlyLinks() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.INTERVIEW_INVITATION),
                classification(EmailCategory.INTERVIEW_INVITATION, true));
        assertEquals(EmailDecisionPolicy.MatchAction.LINK_ONLY, matched(decision, ApplicationStatus.OFFER));
    }

    @Test
    void changedRejectedOrClosedApplicationsNeedAttention() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.OFFER), classification(EmailCategory.OFFER, true));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.REJECTED));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.CLOSED));
    }

    @Test
    void rejectionCanMoveAnActiveApplicationToRejected() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.REJECTION), classification(EmailCategory.REJECTION, true));
        assertEquals(EmailDecisionPolicy.MatchAction.APPLY, matched(decision, ApplicationStatus.OFFER));
    }

    @Test
    void ambiguousMatchNeedsAttention() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.OFFER), classification(EmailCategory.OFFER, true));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION,
                matched(decision, ApplicationStatus.INTERVIEW, true, false));
    }

    @Test
    void conflictingHintsAreUndecidedAndHaveNoProposedStatus() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(EmailCategory.OFFER, EmailCategory.REJECTION),
                classification(EmailCategory.OFFER, true));
        assertEquals(false, decision.decided());
        assertNull(EmailDecisionPolicy.proposedStatus(decision));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.INTERVIEW));
    }

    @Test
    void invalidModelOutputNeedsAttentionAndHasNoProposedStatus() {
        EmailDecisionPolicy.Decision decision = decide(
                Set.of(), classification(EmailCategory.OFFER, false));
        assertEquals(false, decision.decided());
        assertNull(EmailDecisionPolicy.proposedStatus(decision));
        assertEquals(EmailDecisionPolicy.MatchAction.ATTENTION, matched(decision, ApplicationStatus.INTERVIEW));
    }

    private static EmailDecisionPolicy.Decision decide(
            Set<EmailCategory> hints, ClassificationResult result) {
        return EmailDecisionPolicy.decide(hints, result);
    }

    private static EmailDecisionPolicy.MatchAction matched(
            EmailDecisionPolicy.Decision decision, ApplicationStatus current) {
        return matched(decision, current, false, true);
    }

    private static EmailDecisionPolicy.MatchAction matched(
            EmailDecisionPolicy.Decision decision,
            ApplicationStatus current,
            boolean ambiguous,
            boolean requireAgreement) {
        return EmailDecisionPolicy.matchedAction(
                decision, current, ambiguous, true, false, requireAgreement);
    }

    private static ClassificationResult classification(EmailCategory category, boolean valid) {
        return new ClassificationResult(category, "Acme", "Engineer", 0.01, "Wait", false,
                valid, valid ? null : "Model output could not be parsed");
    }
}
