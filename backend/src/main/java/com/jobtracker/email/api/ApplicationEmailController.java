package com.jobtracker.email.api;

import com.jobtracker.application.persistence.JobApplicationRepository;
import com.jobtracker.common.error.NotFoundException;
import com.jobtracker.email.message.EmailMessageRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications/{applicationId}/emails")
public class ApplicationEmailController {
    private final JobApplicationRepository applicationRepository;
    private final EmailMessageRepository messageRepository;

    public ApplicationEmailController(
            JobApplicationRepository applicationRepository,
            EmailMessageRepository messageRepository) {
        this.applicationRepository = applicationRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ApplicationEmailResponse> list(@PathVariable Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new NotFoundException("Job application " + applicationId + " not found");
        }
        return messageRepository.findByApplication_IdOrderByReceivedAtDesc(applicationId).stream()
                .map(message -> new ApplicationEmailResponse(
                        message.getId(), message.getFromAddress(), message.getSubject(),
                        message.getReceivedAt(), message.getCategory(), message.getConfidence()))
                .toList();
    }
}
