package com.jobtracker.email.review;

import com.jobtracker.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Size;

public record ReviewApprovalRequest(
        @Size(max = 200) String company,
        @Size(max = 200) String jobTitle,
        ApplicationStatus status) {
}
