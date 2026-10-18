package com.jobtracker.email;

public record SyncResult(int fetched, int newMessages, int skipped, int errors, boolean throttled) {
}
