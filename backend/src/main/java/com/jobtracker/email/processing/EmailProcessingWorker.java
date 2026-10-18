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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
            EmailCategory ruleHint,
            boolean autoApply,
            double autoApplyMinConfidence) {
        EmailMessage message = findMessage(emailId);
        setClassification(message, classification);
        boolean categoryMismatch = ruleHint != null && classification.category() != ruleHint;
        boolean requiresReview = classification.requiresReview() || !classification.valid() || categoryMismatch;
        if (categoryMismatch && message.getProcessingError() == null) {
            message.setProcessingError("AI category disagreed with the rule hint");
        }

        if (classification.valid() && classification.category() == EmailCategory.UNRELATED) {
            finish(message);
            return new ProcessingOutcome(true, false, false);
        }

        if (!classification.valid()) {
            if (hasProposalDetails(classification)) {
                Optional<JobApplication> duplicate = findExistingApplication(
                        classification.company(), classification.jobTitle());
                if (duplicate.isPresent()) {
                    message.setApplication(duplicate.get());
                    finish(message);
                    return new ProcessingOutcome(true, false, true);
                }
                Optional<ReviewItem> pendingDuplicate = findPendingCreate(
                        classification.company(), classification.jobTitle());
                if (pendingDuplicate.isPresent()) {
                    message.setApplication(pendingDuplicate.get().getApplication());
                    finish(message);
                    return new ProcessingOutcome(true, false, true);
                }
                createProposal(message, null, classification, classification.validationError());
                finish(message);
                return new ProcessingOutcome(true, true, true);
            }
            setProcessingError(message, classification.validationError());
            finish(message);
            return new ProcessingOutcome(true, false, true);
        }

        ApplicationStatus target = EmailProcessingRules.statusFor(classification.category());
        if (target == null) {
            finish(message);
            return new ProcessingOutcome(true, false, false);
        }

        Match match = findMatch(message, classification);
        if (match.ambiguous()) {
            createReview(message, null, ReviewAction.UPDATE_STATUS, target, classification,
                    "Several applications have the same company and title overlap is ambiguous");
            finish(message);
            return new ProcessingOutcome(true, true, false);
        }

        if (match.application() != null) {
            JobApplication application = match.application();
            message.setApplication(application);
            if (EmailProcessingRules.requiresStatusReview(application.getStatus(), target)) {
                createReview(message, application, ReviewAction.UPDATE_STATUS, target, classification,
                        "Status transition requires review");
            } else if (!EmailProcessingRules.isForwardOrUnchanged(application.getStatus(), target)) {
                finish(message);
                return new ProcessingOutcome(true, false, false);
            } else if (application.getStatus() == target) {
                finish(message);
                return new ProcessingOutcome(true, false, false);
            } else if (requiresReview) {
                createReview(message, application, ReviewAction.UPDATE_STATUS, target, classification,
                        reviewReason(classification, categoryMismatch));
            } else if (autoApply
                    && match.matchCount() == 1
                    && classification.confidence() >= autoApplyMinConfidence) {
                applicationService.applyEmailUpdate(
                        application.getId(), null, null, target, message.getProviderMessageId());
                return finishApplied(message, application, false);
            } else {
                createReview(message, application, ReviewAction.UPDATE_STATUS, target, classification,
                        "Automatic updates are disabled or confidence is below the configured threshold");
            }
            finish(message);
            return new ProcessingOutcome(true, true, false);
        }

        if (classification.category() == EmailCategory.APPLICATION_CONFIRMATION
                || hasProposalDetails(classification)) {
            Optional<JobApplication> duplicate = findExistingApplication(
                    classification.company(), classification.jobTitle());
            if (duplicate.isPresent()) {
                message.setApplication(duplicate.get());
                finish(message);
                return new ProcessingOutcome(true, false, false);
            }
            Optional<ReviewItem> pendingDuplicate = findPendingCreate(classification.company(),
                    classification.jobTitle());
            if (pendingDuplicate.isPresent()) {
                message.setApplication(pendingDuplicate.get().getApplication());
                finish(message);
                return new ProcessingOutcome(true, false, false);
            }
            createProposal(message, target, classification,
                    requiresReview ? reviewReason(classification, categoryMismatch) : "New application detected");
            finish(message);
            return new ProcessingOutcome(true, true, false);
        }

        setProcessingError(message, "No matching application or sufficient data for a proposal");
        finish(message);
        return new ProcessingOutcome(true, false, true);
    }

    @Transactional
    public void markNoise(Long emailId) {
        EmailMessage message = findMessage(emailId);
        message.setCategory(EmailCategory.UNRELATED.name());
        message.setClassifiedBy("RULE");
        message.setNextAction("");
        message.setProcessed(true);
        message.setProcessedAt(OffsetDateTime.now());
        message.setProcessingError(null);
    }

    @Transactional
    public void markFailed(Long emailId, String error) {
        EmailMessage message = findMessage(emailId);
        setProcessingError(message, error);
        finish(message);
    }

    private ProcessingOutcome finishApplied(
            EmailMessage message, JobApplication application, boolean proposal) {
        message.setApplication(application);
        finish(message);
        return new ProcessingOutcome(true, proposal, false, true);
    }

    private Match findMatch(EmailMessage message, ClassificationResult classification) {
        if (message.getThreadId() != null && !message.getThreadId().isBlank()) {
            Optional<EmailMessage> threadMatch = messageRepository
                    .findFirstByThreadIdAndApplicationIsNotNullAndIdNotOrderByReceivedAtAsc(
                            message.getThreadId(), message.getId());
            if (threadMatch.isPresent()) {
                return new Match(threadMatch.get().getApplication(), false, 1);
            }
        }
        String company = EmailProcessingRules.normalizeCompany(classification.company());
        if (company.isBlank()) {
            return new Match(null, false, 0);
        }
        List<JobApplication> sameCompany = applicationRepository.findAll().stream()
                .filter(application -> company.equals(EmailProcessingRules.normalizeCompany(application.getCompany())))
                .toList();
        if (sameCompany.size() <= 1) {
            return new Match(sameCompany.isEmpty() ? null : sameCompany.getFirst(), false, sameCompany.size());
        }
        List<JobApplication> titleMatches = sameCompany.stream()
                .filter(application -> EmailProcessingRules.titleTokenOverlap(
                        classification.jobTitle(), application.getJobTitle()) >= TITLE_OVERLAP_THRESHOLD)
                .toList();
        return titleMatches.size() == 1
                ? new Match(titleMatches.getFirst(), false, 1)
                : new Match(null, true, titleMatches.size());
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

    private void createProposal(
            EmailMessage message, ApplicationStatus target, ClassificationResult classification, String reason) {
        createReview(message, null, ReviewAction.CREATE_APPLICATION, target, classification, reason);
    }

    private void createReview(
            EmailMessage message,
            JobApplication application,
            ReviewAction action,
            ApplicationStatus target,
            ClassificationResult classification,
            String reason) {
        ReviewItem item = new ReviewItem();
        item.setEmailMessage(message);
        item.setApplication(application);
        item.setAction(action);
        item.setProposedStatus(target);
        item.setProposedCompany(classification.company());
        item.setProposedJobTitle(classification.jobTitle());
        item.setCategory(classification.category());
        item.setConfidence(toConfidence(classification.confidence()));
        item.setReason(limit(reason, 300));
        item.setStatus(ReviewStatus.PENDING);
        reviewRepository.save(item);
        message.setApplication(application);
    }

    private void setClassification(EmailMessage message, ClassificationResult result) {
        message.setCategory(result.category() == null ? null : result.category().name());
        message.setExtractedCompany(limit(result.company(), 200));
        message.setExtractedJobTitle(limit(result.jobTitle(), 200));
        message.setConfidence(toConfidence(result.confidence()));
        message.setClassifiedBy("AI");
        message.setNextAction(limit(result.nextAction(), 300));
        message.setProcessingError(result.valid() ? null : limit(result.validationError(), 300));
    }

    private void finish(EmailMessage message) {
        message.setProcessed(true);
        message.setProcessedAt(OffsetDateTime.now());
    }

    private void setProcessingError(EmailMessage message, String error) {
        message.setProcessingError(limit(error == null ? "Processing could not classify the message" : error, 300));
    }

    private EmailMessage findMessage(Long id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Email message disappeared during processing"));
    }

    private static boolean hasProposalDetails(ClassificationResult result) {
        return result.company() != null && !result.company().isBlank()
                && result.jobTitle() != null && !result.jobTitle().isBlank();
    }

    private static String reviewReason(ClassificationResult result, boolean categoryMismatch) {
        if (categoryMismatch) {
            return "AI category disagreed with the rule hint";
        }
        return result.requiresReview() ? "The AI requested human review" : "Status update requires confirmation";
    }

    private static String normalizedTitle(String title) {
        return title == null ? "" : title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static BigDecimal toConfidence(Double confidence) {
        return confidence == null || confidence < 0 || confidence > 1
                ? null
                : BigDecimal.valueOf(confidence);
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Match(JobApplication application, boolean ambiguous, int matchCount) {
    }

    public record ProcessingOutcome(boolean processed, boolean proposal, boolean error, boolean autoApplied) {
        public ProcessingOutcome(boolean processed, boolean proposal, boolean error) {
            this(processed, proposal, error, false);
        }
    }
}
