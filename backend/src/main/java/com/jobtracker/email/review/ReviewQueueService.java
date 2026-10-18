package com.jobtracker.email.review;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.application.service.JobApplicationService;
import com.jobtracker.application.persistence.JobApplicationRepository;
import com.jobtracker.common.error.BadRequestException;
import com.jobtracker.common.error.ConflictException;
import com.jobtracker.common.error.NotFoundException;
import com.jobtracker.email.message.EmailMessage;
import com.jobtracker.email.message.EmailMessageRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewQueueService {
    private final ReviewItemRepository reviewRepository;
    private final EmailMessageRepository messageRepository;
    private final JobApplicationService applicationService;
    private final JobApplicationRepository applicationRepository;

    public ReviewQueueService(
            ReviewItemRepository reviewRepository,
            EmailMessageRepository messageRepository,
            JobApplicationService applicationService,
            JobApplicationRepository applicationRepository) {
        this.reviewRepository = reviewRepository;
        this.messageRepository = messageRepository;
        this.applicationService = applicationService;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewQueueResponse> list(ReviewStatus status) {
        return reviewRepository.findAllByStatusOrderByCreatedAtDesc(status).stream()
                .map(ReviewQueueService::toResponse)
                .toList();
    }

    @Transactional
    public void approve(Long id, ReviewApprovalRequest request) {
        ReviewItem item = findPendingItem(id);
        EmailMessage email = item.getEmailMessage();
        ApplicationStatus target = request != null && request.status() != null
                ? request.status() : item.getProposedStatus();
        if (target == null) {
            throw new BadRequestException("Choose a status before applying this attention item");
        }
        JobApplication application;

        if (request != null && request.applicationId() != null) {
            if (request.status() == null) {
                throw new BadRequestException("A status is required when linking to an existing application");
            }
            JobApplication linked = applicationRepository.findById(request.applicationId())
                    .orElseThrow(() -> new NotFoundException(
                            "Job application " + request.applicationId() + " not found"));
            application = applicationService.applyEmailUpdate(
                    linked.getId(), request.company(), request.jobTitle(), target, email.getProviderMessageId());
        } else if (item.getAction() == ReviewAction.CREATE_APPLICATION) {
            String company = override(request == null ? null : request.company(), item.getProposedCompany());
            String jobTitle = override(request == null ? null : request.jobTitle(), item.getProposedJobTitle());
            if (company == null || company.isBlank() || jobTitle == null || jobTitle.isBlank()) {
                throw new BadRequestException("Company and job title are required to create an application");
            }
            application = applicationService.createFromEmail(
                    company, jobTitle, target, email.getReceivedAt().atZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate(),
                    email.getSubject(), email.getProviderMessageId());
        } else {
            if (item.getApplication() == null) {
                throw new BadRequestException("Choose an existing application or provide company and job title");
            }
            application = applicationService.applyEmailUpdate(
                    item.getApplication().getId(),
                    request == null ? null : request.company(),
                    request == null ? null : request.jobTitle(),
                    target,
                    email.getProviderMessageId());
        }

        item.setApplication(application);
        item.setStatus(ReviewStatus.APPROVED);
        item.setResolvedAt(OffsetDateTime.now());
        email.setApplication(application);
        messageRepository.save(email);
    }

    @Transactional
    public void reject(Long id) {
        ReviewItem item = findPendingItem(id);
        item.setStatus(ReviewStatus.REJECTED);
        item.setResolvedAt(OffsetDateTime.now());
    }

    private ReviewItem findPendingItem(Long id) {
        ReviewItem item = reviewRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Review item " + id + " not found"));
        if (item.getStatus() != ReviewStatus.PENDING) {
            throw new ConflictException("Review item " + id + " is not pending");
        }
        return item;
    }

    private static String override(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private static ReviewQueueResponse toResponse(ReviewItem item) {
        EmailMessage email = item.getEmailMessage();
        JobApplication application = item.getApplication();
        return new ReviewQueueResponse(
                item.getId(),
                email.getId(),
                item.getAction(),
                item.getProposedStatus(),
                item.getProposedCompany(),
                item.getProposedJobTitle(),
                item.getCategory(),
                item.getConfidence(),
                item.getReason(),
                item.getStatus(),
                item.getCreatedAt(),
                email.getSubject(),
                email.getFromAddress(),
                email.getReceivedAt(),
                email.getSnippet(),
                application == null ? null : application.getId(),
                application == null ? null : application.getCompany(),
                application == null ? null : application.getJobTitle(),
                application == null ? null : application.getStatus());
    }
}
