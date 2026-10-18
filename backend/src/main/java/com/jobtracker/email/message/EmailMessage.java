package com.jobtracker.email.message;

import com.jobtracker.application.domain.JobApplication;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "email_message")
@Getter
@Setter
public class EmailMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_message_id", nullable = false, unique = true, length = 255)
    private String providerMessageId;

    @Column(name = "thread_id", length = 255)
    private String threadId;

    @Column(name = "from_address", columnDefinition = "TEXT")
    private String fromAddress;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(nullable = false)
    private boolean processed;

    @Column(length = 40)
    private String category;

    @Column(name = "extracted_company", length = 200)
    private String extractedCompany;

    @Column(name = "extracted_job_title", length = 200)
    private String extractedJobTitle;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "classified_by", length = 10)
    private String classifiedBy;

    @Column(name = "next_action", length = 300)
    private String nextAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Column(name = "processing_error", length = 300)
    private String processingError;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public EmailMessage() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
