package com.jobtracker.email;

import java.time.OffsetDateTime;

public record SyncSummary(int fetched, int newMessages, int skipped, int errors, OffsetDateTime completedAt) {
    public SyncResult result() {
        return new SyncResult(fetched, newMessages, skipped, errors);
    }
}
