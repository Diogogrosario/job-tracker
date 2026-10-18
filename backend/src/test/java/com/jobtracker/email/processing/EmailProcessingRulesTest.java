package com.jobtracker.email.processing;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.email.processing.EmailCategory;
import com.jobtracker.email.processing.EmailProcessingRules;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailProcessingRulesTest {
    @Test
    void normalizesCompanyNamesAndLegalSuffixes() {
        assertEquals("acme", EmailProcessingRules.normalizeCompany("ACME, Inc."));
        assertEquals("cafe systems", EmailProcessingRules.normalizeCompany("Café Systems, Lda."));
    }

    @Test
    void matchesTitlesWhenTokenOverlapIsAtLeastHalf() {
        assertEquals(2.0 / 3.0, EmailProcessingRules.titleTokenOverlap(
                "Senior Java Developer", "Java Developer"));
        assertTrue(EmailProcessingRules.titleTokenOverlap(
                "Senior Java Developer", "Java Developer") >= 0.5);
        assertFalse(EmailProcessingRules.titleTokenOverlap(
                "Product Designer", "Senior Java Developer") >= 0.5);
    }

    @Test
    void onlyMovesThroughForwardStatusesWithoutImplicitBackwardChanges() {
        assertTrue(EmailProcessingRules.isForwardOrUnchanged(
                ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW));
        assertTrue(EmailProcessingRules.isForwardOrUnchanged(
                ApplicationStatus.INTERVIEW, ApplicationStatus.INTERVIEW));
        assertFalse(EmailProcessingRules.isForwardOrUnchanged(
                ApplicationStatus.OFFER, ApplicationStatus.INTERVIEW));
        assertTrue(EmailProcessingRules.requiresStatusReview(
                ApplicationStatus.CLOSED, ApplicationStatus.OFFER));
        assertTrue(EmailProcessingRules.requiresStatusReview(
                ApplicationStatus.APPLIED, ApplicationStatus.REJECTED));
    }

    @Test
    void mapsCategoriesToApplicationStatuses() {
        assertEquals(ApplicationStatus.APPLIED,
                EmailProcessingRules.statusFor(EmailCategory.APPLICATION_CONFIRMATION));
        assertEquals(ApplicationStatus.INTERVIEW,
                EmailProcessingRules.statusFor(EmailCategory.INTERVIEW_INVITATION));
        assertEquals(ApplicationStatus.INTERVIEW,
                EmailProcessingRules.statusFor(EmailCategory.ASSESSMENT));
        assertEquals(ApplicationStatus.OFFER, EmailProcessingRules.statusFor(EmailCategory.OFFER));
        assertEquals(ApplicationStatus.REJECTED, EmailProcessingRules.statusFor(EmailCategory.REJECTION));
        assertEquals(null, EmailProcessingRules.statusFor(EmailCategory.UNRELATED));
    }

    @Test
    void detectsAdvisoryRuleHintsAndNoiseSubjects() {
        assertEquals(EmailCategory.APPLICATION_CONFIRMATION, EmailProcessingRules.ruleHint(
                "Thank you for applying", ""));
        assertEquals(EmailCategory.REJECTION, EmailProcessingRules.ruleHint(
                "Update", "We decided to proceed with other candidates"));
        assertEquals(EmailCategory.INTERVIEW_INVITATION, EmailProcessingRules.ruleHint(
                "Interview invitation", ""));
        assertEquals(EmailCategory.ASSESSMENT, EmailProcessingRules.ruleHint(
                "Online assessment", ""));
        assertEquals(EmailCategory.ASSESSMENT, EmailProcessingRules.ruleHint(
                "Coding challenge", "This take-home exercise is due soon."));
        assertEquals(EmailCategory.OFFER, EmailProcessingRules.ruleHint(
                "Pleased to offer you the role", ""));
        assertTrue(EmailProcessingRules.isNoise("Your weekly digest: recommended jobs"));
        assertEquals(null, EmailProcessingRules.ruleHint("General update", "No classification phrase."));
    }
}
