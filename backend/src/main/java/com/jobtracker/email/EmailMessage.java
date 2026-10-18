package com.jobtracker.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EmailMessage() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
