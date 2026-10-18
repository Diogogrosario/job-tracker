package com.jobtracker.common.error;

import java.time.Instant;

public record ApiError(int status, String error, String message, Instant timestamp) {
}