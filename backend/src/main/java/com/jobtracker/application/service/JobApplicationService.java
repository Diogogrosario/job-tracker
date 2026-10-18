package com.jobtracker.application.service;

import com.jobtracker.application.api.ApplicationStatusHistoryResponse;
import com.jobtracker.application.api.JobApplicationRequest;
import com.jobtracker.application.api.JobApplicationResponse;
import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.ApplicationStatusHistory;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.application.persistence.ApplicationStatusHistoryRepository;
import com.jobtracker.application.persistence.JobApplicationRepository;
import com.jobtracker.common.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.LocalDate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {
    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;

    public JobApplicationService(
            JobApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository historyRepository) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public JobApplicationResponse create(JobApplicationRequest request) {
        JobApplication application = new JobApplication();
        application.setCompany(request.company());
        application.setJobTitle(request.jobTitle());
        application.setJobUrl(request.jobUrl());
        application.setDateApplied(request.dateApplied());
        application.setStatus(request.status() == null ? ApplicationStatus.APPLIED : request.status());
        application.setNotes(request.notes());
        application = applicationRepository.save(application);
        recordStatusChange(application, null, application.getStatus(), "MANUAL", null);
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse get(Long id) {
        return toResponse(findApplication(id));
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> list(ApplicationStatus status, String search) {
        Specification<JobApplication> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                Predicate companyMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("company")), pattern, '\\');
                Predicate titleMatches = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("jobTitle")), pattern, '\\');
                predicates.add(criteriaBuilder.or(companyMatches, titleMatches));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return applicationRepository.findAll(specification, sort).stream()
                .map(JobApplicationService::toResponse)
                .toList();
    }

    @Transactional
    public JobApplicationResponse update(Long id, JobApplicationRequest request) {
        JobApplication application = findApplication(id);
        ApplicationStatus oldStatus = application.getStatus();
        ApplicationStatus newStatus = request.status() == null ? oldStatus : request.status();

        application.setCompany(request.company());
        application.setJobTitle(request.jobTitle());
        application.setJobUrl(request.jobUrl());
        application.setDateApplied(request.dateApplied());
        application.setStatus(newStatus);
        application.setNotes(request.notes());
        application = applicationRepository.save(application);

        if (!Objects.equals(oldStatus, newStatus)) {
            recordStatusChange(application, oldStatus, newStatus, "MANUAL", null);
        }
        return toResponse(application);
    }

    @Transactional
    public void delete(Long id) {
        applicationRepository.delete(findApplication(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistoryResponse> history(Long id) {
        findApplication(id);
        return historyRepository.findByApplication_IdOrderByChangedAtAsc(id).stream()
                .map(JobApplicationService::toHistoryResponse)
                .toList();
    }

    @Transactional
    public JobApplication createFromEmail(
            String company, String jobTitle, ApplicationStatus status, LocalDate dateApplied,
            String subject, String sourceMessageId) {
        JobApplication application = new JobApplication();
        application.setCompany(company);
        application.setJobTitle(jobTitle);
        application.setDateApplied(dateApplied);
        application.setStatus(status == null ? ApplicationStatus.APPLIED : status);
        application.setNotes("Created from email: " + (subject == null ? "" : subject));
        application = applicationRepository.save(application);
        recordStatusChange(application, null, application.getStatus(), "EMAIL", sourceMessageId);
        return application;
    }

    @Transactional
    public JobApplication applyEmailUpdate(
            Long id, String company, String jobTitle, ApplicationStatus status, String sourceMessageId) {
        JobApplication application = findApplication(id);
        if (company != null) {
            application.setCompany(company);
        }
        if (jobTitle != null) {
            application.setJobTitle(jobTitle);
        }
        ApplicationStatus oldStatus = application.getStatus();
        if (status != null) {
            application.setStatus(status);
        }
        application = applicationRepository.save(application);
        if (!Objects.equals(oldStatus, application.getStatus())) {
            recordStatusChange(application, oldStatus, application.getStatus(), "EMAIL", sourceMessageId);
        }
        return application;
    }

    private JobApplication findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job application " + id + " not found"));
    }

    private void recordStatusChange(
            JobApplication application,
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            String source,
            String sourceMessageId) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(application);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setSource(source);
        history.setSourceMessageId(sourceMessageId);
        historyRepository.save(history);
    }

    private static JobApplicationResponse toResponse(JobApplication application) {
        return new JobApplicationResponse(
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                application.getJobUrl(),
                application.getDateApplied(),
                application.getStatus(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private static ApplicationStatusHistoryResponse toHistoryResponse(ApplicationStatusHistory history) {
        return new ApplicationStatusHistoryResponse(
                history.getId(),
                history.getOldStatus(),
                history.getNewStatus(),
                history.getSource(),
                history.getSourceMessageId(),
                history.getChangedAt());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}