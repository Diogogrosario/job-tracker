package com.jobtracker.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "mailbox_connection")
@Getter
@Setter
public class MailboxConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String provider;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MailboxStatus status;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private OffsetDateTime connectedAt;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_sync_status", length = 30)
    private String lastSyncStatus;

    @Column(name = "last_sync_summary", columnDefinition = "TEXT")
    private String lastSyncSummary;

    protected MailboxConnection() {
    }

    @PrePersist
    protected void onCreate() {
        if (connectedAt == null) {
            connectedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
