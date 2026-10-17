package com.jobtracker.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record JobApplicationRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String jobTitle,
        @Size(max = 1000) String jobUrl,
        LocalDate dateApplied,
        ApplicationStatus status,
        String notes) {
}