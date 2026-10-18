package com.jobtracker.email.sync;

public record SyncResult(int fetched, int newMessages, int skipped, int errors, boolean throttled) {
}
