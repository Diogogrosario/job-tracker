package com.jobtracker.email.mailbox;

import com.jobtracker.config.properties.AppProperties;
import com.jobtracker.email.message.EmailMessageRepository;
import com.jobtracker.email.sync.SyncSummary;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MailboxService {
    private static final String PROVIDER = "GMAIL";
    private static final String REVOKE_URI = "https://oauth2.googleapis.com/revoke";

    private final RestClient restClient;
    private final AppProperties properties;
    private final MailboxConnectionRepository connectionRepository;
    private final EmailMessageRepository messageRepository;
    private final TokenEncryptionService encryptionService;
    private final GmailTokenService tokenService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public MailboxService(
            RestClient gmailRestClient,
            AppProperties properties,
            MailboxConnectionRepository connectionRepository,
            EmailMessageRepository messageRepository,
            TokenEncryptionService encryptionService,
            GmailTokenService tokenService,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.restClient = gmailRestClient;
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.messageRepository = messageRepository;
        this.encryptionService = encryptionService;
        this.tokenService = tokenService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public MailboxStatusResponse status() {
        return connectionRepository.findByProvider(PROVIDER)
                .map(connection -> new MailboxStatusResponse(
                        connection.getStatus() == MailboxStatus.CONNECTED,
                        connection.getStatus() == MailboxStatus.NEEDS_RECONNECT,
                        connection.getEmail(),
                        connection.getLastSyncAt(),
                        connection.getLastSyncStatus(),
                        messageRepository.count()))
                .orElseGet(() -> new MailboxStatusResponse(
                        false, false, null, null, null, messageRepository.count()));
    }

    public void disconnect() {
        connectionRepository.findByProvider(PROVIDER).ifPresent(connection -> {
            try {
                revoke(encryptionService.decrypt(connection.getEncryptedRefreshToken()));
            } catch (EmailIntegrationException | RestClientException exception) {
                // Disconnect must still remove the local credential if revocation fails.
            }
            connectionRepository.delete(connection);
        });
        tokenService.clearAccessToken();
    }

    public void deleteImportedMessages() {
        messageRepository.deleteAllInBatch();
    }

    public String frontendUrl() {
        return appProperties.getFrontendUrl();
    }

    public SyncStatusResponse syncStatus(boolean syncing) {
        return connectionRepository.findByProvider(PROVIDER)
                .map(connection -> new SyncStatusResponse(
                        readSummary(connection.getLastSyncSummary()),
                        connection.getLastSyncAt(),
                        connection.getLastSyncStatus(),
                        syncing))
                .orElseGet(() -> new SyncStatusResponse(null, null, null, syncing));
    }

    private void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        restClient.post()
                .uri(REVOKE_URI)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private SyncSummary readSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(summary, SyncSummary.class);
        } catch (JacksonException exception) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the stored Gmail sync result");
        }
    }

    public record MailboxStatusResponse(
            boolean connected,
            boolean needsReconnect,
            String email,
            OffsetDateTime lastSyncAt,
            String lastSyncStatus,
            long messageCount) {
    }

    public record SyncStatusResponse(
            SyncSummary lastSyncResult,
            OffsetDateTime lastSyncAt,
            String lastSyncStatus,
            boolean syncing) {
    }
}
