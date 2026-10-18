package com.jobtracker.email.sync;

import com.jobtracker.config.properties.AppProperties;
import com.jobtracker.email.mailbox.EmailIntegrationException;
import com.jobtracker.email.mailbox.GmailApiClient;
import com.jobtracker.email.mailbox.GmailTokenService;
import com.jobtracker.email.mailbox.MailboxConnection;
import com.jobtracker.email.mailbox.MailboxConnectionRepository;
import com.jobtracker.email.mailbox.MailboxStatus;
import com.jobtracker.email.message.EmailMessage;
import com.jobtracker.email.message.EmailMessageRepository;
import com.jobtracker.email.processing.EmailProcessingService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmailSyncService {
    private static final String PROVIDER = "GMAIL";
    private static final int BODY_MAX_LENGTH = 10_000;

    private final AppProperties properties;
    private final MailboxConnectionRepository connectionRepository;
    private final EmailMessageRepository messageRepository;
    private final GmailTokenService tokenService;
    private final GmailApiClient gmailApiClient;
    private final ObjectMapper objectMapper;
    private final EmailProcessingService processingService;
    private final AtomicBoolean syncing = new AtomicBoolean();

    public EmailSyncService(
            AppProperties properties,
            MailboxConnectionRepository connectionRepository,
            EmailMessageRepository messageRepository,
            GmailTokenService tokenService,
            GmailApiClient gmailApiClient,
            ObjectMapper objectMapper,
            EmailProcessingService processingService) {
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.messageRepository = messageRepository;
        this.tokenService = tokenService;
        this.gmailApiClient = gmailApiClient;
        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

    public SyncResult sync(boolean force) {
        if (!syncing.compareAndSet(false, true)) {
            throw new EmailIntegrationException(HttpStatus.CONFLICT, "A Gmail sync is already running");
        }
        SyncResult result;
        try {
            result = runSync(force);
        } finally {
            syncing.set(false);
        }
        if (result.errors() == 0) {
            processingService.trigger();
        }
        return result;
    }

    public boolean isSyncing() {
        return syncing.get();
    }

    private SyncResult runSync(boolean force) {
        MailboxConnection connection = connectionRepository.findByProvider(PROVIDER)
                .orElseThrow(() -> new EmailIntegrationException(HttpStatus.CONFLICT, "Gmail is not connected"));
        if (connection.getStatus() != MailboxStatus.CONNECTED) {
            throw new EmailIntegrationException(HttpStatus.CONFLICT, "Gmail connection needs to be reconnected");
        }
        if (!force && hasRecentSuccessfulSync(connection)) {
            return readSummary(connection.getLastSyncSummary()).result(true);
        }

        SyncCounters counters = new SyncCounters();
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            String accessToken = tokenService.getAccessToken();
            String dateQuery = buildDateQuery(connection.getLastSyncAt());
            String query = dateQuery + " " + properties.getGmail().getSyncQuery();
            String pageToken = null;
            int maxMessages = Math.max(1, properties.getGmail().getMaxMessagesPerSync());
            while (counters.fetched < maxMessages) {
                int pageSize = Math.min(500, maxMessages - counters.fetched);
                GmailApiClient.MessagePage page = gmailApiClient.listMessages(
                        accessToken, query, pageSize, pageToken);
                for (String messageId : page.ids()) {
                    if (counters.fetched >= maxMessages) {
                        break;
                    }
                    counters.fetched++;
                    processMessage(accessToken, messageId, counters);
                }
                if (page.nextPageToken() == null || page.nextPageToken().isBlank()) {
                    break;
                }
                pageToken = page.nextPageToken();
            }
            completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            SyncSummary summary = counters.toSummary(completedAt);
            connection.setLastSyncStatus(counters.errors == 0 ? "SUCCESS" : "PARTIAL");
            connection.setLastSyncSummary(serialize(summary));
            if (counters.errors == 0) {
                connection.setLastSyncAt(completedAt);
            }
            connectionRepository.save(connection);
            return summary.result();
        } catch (EmailIntegrationException exception) {
            if (exception.getStatus() == HttpStatus.CONFLICT
                    || exception.getStatus() == HttpStatus.BAD_GATEWAY) {
                recordListingFailure(connection, counters, completedAt);
            }
            if (exception.getStatus() == HttpStatus.BAD_GATEWAY) {
                return new SyncResult(counters.fetched, counters.newMessages, counters.skipped, counters.errors, false);
            }
            throw exception;
        }
    }

    private boolean hasRecentSuccessfulSync(MailboxConnection connection) {
        OffsetDateTime lastSyncAt = connection.getLastSyncAt();
        int minIntervalMinutes = properties.getGmail().getMinSyncIntervalMinutes();
        return "SUCCESS".equals(connection.getLastSyncStatus())
                && lastSyncAt != null
                && minIntervalMinutes > 0
                && lastSyncAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minIntervalMinutes));
    }

    private SyncSummary readSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the stored Gmail sync result");
        }
        try {
            return objectMapper.readValue(summary, SyncSummary.class);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the stored Gmail sync result");
        }
    }

    private void processMessage(String accessToken, String messageId, SyncCounters counters) {
        if (messageRepository.existsByProviderMessageId(messageId)) {
            counters.skipped++;
            return;
        }
        try {
            JsonNode message = gmailApiClient.getMessage(accessToken, messageId);
            messageRepository.save(toEntity(message, messageId));
            counters.newMessages++;
        } catch (RuntimeException exception) {
            if (messageRepository.existsByProviderMessageId(messageId)) {
                counters.skipped++;
            } else {
                counters.errors++;
            }
        }
    }

    private SyncResult recordListingFailure(
            MailboxConnection connection, SyncCounters counters, OffsetDateTime completedAt) {
        counters.errors++;
        SyncSummary summary = counters.toSummary(completedAt);
        connection.setLastSyncStatus("FAILED");
        connection.setLastSyncSummary(serialize(summary));
        connectionRepository.save(connection);
        return summary.result();
    }

    private String buildDateQuery(OffsetDateTime lastSyncAt) {
        if (lastSyncAt == null) {
            return "newer_than:" + Math.max(1, properties.getGmail().getInitialDays()) + "d";
        }
        long startEpoch = lastSyncAt.toInstant().minusSeconds(24 * 60 * 60).getEpochSecond();
        return "after:" + startEpoch;
    }

    private String serialize(SyncSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the Gmail sync result");
        }
    }

    private EmailMessage toEntity(JsonNode message, String messageId) {
        EmailMessage entity = new EmailMessage();
        entity.setProviderMessageId(messageId);
        entity.setThreadId(text(message, "threadId"));
        entity.setSnippet(text(message, "snippet"));
        JsonNode payload = message.path("payload");
        entity.setFromAddress(header(payload, "From"));
        entity.setSubject(header(payload, "Subject"));
        entity.setReceivedAt(receivedAt(message, payload));
        entity.setBodyText(readBody(payload));
        return entity;
    }

    private OffsetDateTime receivedAt(JsonNode message, JsonNode payload) {
        String internalDate = text(message, "internalDate");
        if (internalDate != null) {
            return Instant.ofEpochMilli(Long.parseLong(internalDate)).atOffset(ZoneOffset.UTC);
        }
        String headerDate = header(payload, "Date");
        if (headerDate != null) {
            return ZonedDateTime.parse(headerDate, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toOffsetDateTime();
        }
        throw new IllegalArgumentException("Gmail message has no received date");
    }

    private String readBody(JsonNode payload) {
        List<String> plainBodies = new ArrayList<>();
        List<String> htmlBodies = new ArrayList<>();
        collectBodies(payload, plainBodies, htmlBodies);
        String body = plainBodies.isEmpty()
                ? htmlBodies.isEmpty() ? "" : stripHtml(htmlBodies.get(0))
                : plainBodies.get(0);
        return body.length() > BODY_MAX_LENGTH ? body.substring(0, BODY_MAX_LENGTH) : body;
    }

    private void collectBodies(JsonNode part, List<String> plainBodies, List<String> htmlBodies) {
        String mimeType = text(part, "mimeType");
        String data = text(part.path("body"), "data");
        if (data != null && !data.isBlank()) {
            String decoded = new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
            if ("text/plain".equalsIgnoreCase(mimeType)) {
                plainBodies.add(decoded);
            } else if ("text/html".equalsIgnoreCase(mimeType)) {
                htmlBodies.add(decoded);
            }
        }
        JsonNode parts = part.path("parts");
        if (parts.isArray()) {
            for (JsonNode nested : parts) {
                collectBodies(nested, plainBodies, htmlBodies);
            }
        }
    }

    private static String stripHtml(String html) {
        String withoutActiveContent = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ");
        String withoutTags = withoutActiveContent.replaceAll("(?s)<[^>]*>", " ");
        return HtmlUtils.htmlUnescape(withoutTags).replaceAll("\\s+", " ").trim();
    }

    private static String header(JsonNode payload, String name) {
        JsonNode headers = payload.path("headers");
        if (!headers.isArray()) {
            return null;
        }
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(text(header, "name"))) {
                return text(header, "value");
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString(null);
    }

    private static final class SyncCounters {
        private int fetched;
        private int newMessages;
        private int skipped;
        private int errors;

        private SyncSummary toSummary(OffsetDateTime completedAt) {
            return new SyncSummary(fetched, newMessages, skipped, errors, completedAt);
        }
    }
}
