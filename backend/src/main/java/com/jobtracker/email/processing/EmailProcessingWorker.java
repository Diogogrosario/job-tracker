package com.jobtracker.email.processing;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.application.persistence.JobApplicationRepository;
import com.jobtracker.application.service.JobApplicationService;
import com.jobtracker.email.message.EmailMessage;
import com.jobtracker.email.message.EmailMessageRepository;
import com.jobtracker.email.review.ReviewAction;
import com.jobtracker.email.review.ReviewItem;
import com.jobtracker.email.review.ReviewItemRepository;
import com.jobtracker.email.review.ReviewStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailProcessingWorker {
    private static final double TITLE_OVERLAP_THRESHOLD = 0.5;

    private final EmailMessageRepository messageRepository;
    private final ReviewItemRepository reviewRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobApplicationService applicationService;

    public EmailProcessingWorker(
            EmailMessageRepository messageRepository,
            ReviewItemRepository reviewRepository,
            JobApplicationRepository applicationRepository,
            JobApplicationService applicationService) {
        this.messageRepository = messageRepository;
        this.reviewRepository = reviewRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
    }

    @Transactional
    public ProcessingOutcome process(
            Long emailId,
            ClassificationResult classification,
            Set<EmailCategory> ruleHints,
            boolean autoApplyMatched,
            boolean autoApplyAiOnly,
            boolean requireAgreement) {
        EmailMessage message = findMessage(emailId);
        EmailDecisionPolicy.Decision decision = EmailDecisionPolicy.decide(ruleHints, classification);
        setClassification(message, classification, decision);

        Optional<ReviewItem> existingItem = reviewRepository.findFirstByEmailMessage_Id(message.getId());
        if (existingItem.isPresent()) {
            finish(message);
            return new ProcessingOutcome(true, false, false, false);
        }

        if (classification.valid() && classification.category() == EmailCategory.UNRELATED
                && ruleHints.size() < 2 && !decision.decided()) {
            finish(message);
            return new ProcessingOutcome(true, false, false, false);
        }
        if (decision.category() == EmailCategory.UNRELATED && decision.decided()) {
            finish(message);
            return new ProcessingOutcome(true, false, false, false);
        }

        Match match = findMatch(message, classification);
        ApplicationStatus target = EmailDecisionPolicy.suggestedStatus(decision);

        if (!classification.valid()) {
            String reason = match.ambiguous()
                    ? "Several applications match this company"
                    : requireAgreement && !ruleHints.isEmpty()
                            ? EmailDecisionPolicy.agreementReason(ruleHints, classification)
                            : decision.reason() == null ? "Model output could not be parsed" : decision.reason();
            if (match.application() != null) {
                message.setApplication(match.application());
                createReview(message, match.application(), ReviewAction.UPDATE_STATUS,
                        target, decision, classification, reason);
            } else {
                createReview(message, null, ReviewAction.CREATE_APPLICATION,
                        target, decision, classification, reason);
            }
            finish(message);
            return new ProcessingOutcome(true, true, true, false);
        }

        if (match.ambiguous()) {
            createReview(message, null, ReviewAction.CREATE_APPLICATION, target, decision, classification,
                    "Several applications match this company");
            finish(message);
            return new ProcessingOutcome(true, true, false, false);
        }

        if (match.application() != null) {
            JobApplication application = match.application();
            message.setApplication(application);
            EmailDecisionPolicy.MatchAction action = EmailDecisionPolicy.matchedAction(
                    decision, application.getStatus(), match.ambiguous(),
                    autoApplyMatched, autoApplyAiOnly, requireAgreement);
            if (action == EmailDecisionPolicy.MatchAction.LINK_ONLY) {
                finish(message);
                return new ProcessingOutcome(true, false, false, false);
            }
            if (action == EmailDecisionPolicy.MatchAction.ATTENTION) {
                String reason = decision.reason();
                if (reason == null && application.getStatus() == ApplicationStatus.REJECTED) {
                    reason = "Application is already rejected";
                } else if (reason == null && application.getStatus() == ApplicationStatus.CLOSED) {
                    reason = "Application is already closed";
                } else if (reason == null && requireAgreement && !decision.aiAgreesWithRule()) {
                    reason = EmailDecisionPolicy.agreementReason(ruleHints, classification);
                } else if (reason == null && !decision.decided()) {
                    reason = "Category could not be decided";
                } else if (reason == null && !autoApplyAiOnly
                        && "AI".equals(decision.classifiedBy())) {
                    reason = "AI-only classification requires attention";
                } else if (reason == null) {
                    reason = "Matched application needs attention";
                }
                createReview(message, application, ReviewAction.UPDATE_STATUS, target, decision, classification,
                        reason);
                finish(message);
                return new ProcessingOutcome(true, true, false, false);
            }
            applicationService.applyEmailUpdate(
                    application.getId(), null, null, target, message.getProviderMessageId());
            finish(message);
            return new ProcessingOutcome(true, false, false, true);
        }

        if (hasProposalDetails(classification)) {
            Optional<JobApplication> duplicateApplication = findExistingApplication(
                    classification.company(), classification.jobTitle());
            if (duplicateApplication.isPresent()) {
                message.setApplication(duplicateApplication.get());
                finish(message);
                return new ProcessingOutcome(true, false, false, false);
            }
            Optional<ReviewItem> duplicateProposal = findPendingCreate(
                    classification.company(), classification.jobTitle());
            if (duplicateProposal.isPresent()) {
                message.setApplication(duplicateProposal.get().getApplication());
                finish(message);
                return new ProcessingOutcome(true, false, false, false);
            }
            if (classification.category() == EmailCategory.APPLICATION_CONFIRMATION || decision.decided()
                    || !classification.valid()) {
                createReview(message, null, ReviewAction.CREATE_APPLICATION, target, decision, classification,
                        decision.reason() == null ? "No matching application" : decision.reason());
                finish(message);
                return new ProcessingOutcome(true, true, false, false);
            }
        }

        createReview(message, null, ReviewAction.CREATE_APPLICATION, null, decision, classification,
                decision.reason() == null ? "No matching application" : decision.reason());
        finish(message);
        return new ProcessingOutcome(true, true, false, false);
    }

    @Transactional
    public void markNoise(Long emailId) {
        EmailMessage message = findMessage(emailId);
        message.setCategory(EmailCategory.UNRELATED.name());
        message.setConfidence(new java.math.BigDecimal("0.800"));
        finish(message);
    }

    @Transactional
    public void markFailed(Long emailId, String error) {
        EmailMessage message = findMessage(emailId);
        message.setCategory(EmailCategory.UNRELATED.name());
        message.setConfidence(new java.math.BigDecimal("0.000"));
        finish(message);
    }

    private Match findMatch(EmailMessage message, ClassificationResult classification) {
        if (message.getThreadId() != null && !message.getThreadId().isBlank()) {
            Optional<EmailMessage> threadMatch = messageRepository
                    .findFirstByThreadIdAndApplicationIsNotNullAndIdNotOrderByReceivedAtAsc(
                            message.getThreadId(), message.getId());
            if (threadMatch.isPresent()) {
                return new Match(threadMatch.get().getApplication(), false);
            }
        }
        String company = EmailProcessingRules.normalizeCompany(classification.company());
        if (company.isBlank()) {
            return new Match(null, false);
        }
        List<JobApplication> sameCompany = applicationRepository.findAll().stream()
                .filter(application -> company.equals(EmailProcessingRules.normalizeCompany(application.getCompany())))
                .toList();
        if (sameCompany.size() == 1) {
            return new Match(sameCompany.getFirst(), false);
        }
        if (sameCompany.isEmpty()) {
            return new Match(null, false);
        }
        List<JobApplication> titleMatches = sameCompany.stream()
                .filter(application -> EmailProcessingRules.titleTokenOverlap(
                        classification.jobTitle(), application.getJobTitle()) >= TITLE_OVERLAP_THRESHOLD)
                .toList();
        return titleMatches.size() == 1
                ? new Match(titleMatches.getFirst(), false)
                : new Match(null, true);
    }

    private Optional<JobApplication> findExistingApplication(String company, String jobTitle) {
        String companyKey = EmailProcessingRules.normalizeCompany(company);
        String titleKey = normalizedTitle(jobTitle);
        return applicationRepository.findAll().stream()
                .filter(application -> companyKey.equals(
                        EmailProcessingRules.normalizeCompany(application.getCompany())))
                .filter(application -> titleKey.equals(normalizedTitle(application.getJobTitle())))
                .findFirst();
    }

    private Optional<ReviewItem> findPendingCreate(String company, String jobTitle) {
        String companyKey = EmailProcessingRules.normalizeCompany(company);
        String titleKey = normalizedTitle(jobTitle);
        return reviewRepository.findAllByActionAndStatus(ReviewAction.CREATE_APPLICATION, ReviewStatus.PENDING)
                .stream()
                .filter(item -> companyKey.equals(EmailProcessingRules.normalizeCompany(item.getProposedCompany())))
                .filter(item -> titleKey.equals(normalizedTitle(item.getProposedJobTitle())))
                .findFirst();
    }

    private void createReview(
            EmailMessage message,
            JobApplication application,
            ReviewAction action,
            ApplicationStatus target,
            EmailDecisionPolicy.Decision decision,
            ClassificationResult classification,
            String reason) {
        ReviewItem item = new ReviewItem();
        item.setEmailMessage(message);
        item.setApplication(application);
        item.setAction(action);
        item.setProposedStatus(target);
        item.setProposedCompany(classification.company());
        item.setProposedJobTitle(classification.jobTitle());
        item.setCategory(decision.category() == null ? classification.category() : decision.category());
        item.setConfidence(decision.confidence());
        item.setReason(limit(reason == null ? "Classification needs attention" : reason, 300));
        item.setStatus(ReviewStatus.PENDING);
        reviewRepository.save(item);
        message.setApplication(application);
    }

    private void setClassification(
            EmailMessage message,
            ClassificationResult classification,
            EmailDecisionPolicy.Decision decision) {
        message.setCategory(decision.category() == null ? null : decision.category().name());
        message.setConfidence(decision.confidence());
    }

    private void finish(EmailMessage message) {
        message.setProcessed(true);
    }

    private EmailMessage findMessage(Long id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Email message disappeared during processing"));
    }

    private static boolean hasProposalDetails(ClassificationResult result) {
        return result.company() != null && !result.company().isBlank()
                && result.jobTitle() != null && !result.jobTitle().isBlank();
    }

    private static String normalizedTitle(String title) {
        return title == null ? "" : title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Match(JobApplication application, boolean ambiguous) {
    }

    public record ProcessingOutcome(boolean processed, boolean proposal, boolean error, boolean autoApplied) {
    }
}
