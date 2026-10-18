package com.jobtracker.application.persistence;

import java.time.LocalDate;
import java.time.Instant;

public interface JobApplicationRow {
    Long getId();

    String getCompany();

    String getJobTitle();

    String getJobUrl();

    LocalDate getDateApplied();

    String getStatus();

    String getNotes();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    String getLastStatusSource();

    String getLastEmailSubject();

    Instant getLastEmailAt();
}
