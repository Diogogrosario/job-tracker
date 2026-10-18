package com.jobtracker.email.sync;

import java.time.OffsetDateTime;

public record SyncSummary(int fetched, int newMessages, int skipped, int errors, OffsetDateTime completedAt) {
    public SyncResult result() {
        return result(false);
    }

    public SyncResult result(boolean throttled) {
        return new SyncResult(fetched, newMessages, skipped, errors, throttled);
    }
}
