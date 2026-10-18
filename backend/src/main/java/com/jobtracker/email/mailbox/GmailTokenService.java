package com.jobtracker.email.mailbox;

import com.jobtracker.config.properties.AppProperties;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Service
public class GmailTokenService {
    private static final String PROVIDER = "GMAIL";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final RestClient restClient;
    private final AppProperties properties;
    private final MailboxConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;
    private CachedAccessToken cachedAccessToken;

    public GmailTokenService(
            RestClient gmailRestClient,
            AppProperties properties,
            MailboxConnectionRepository connectionRepository,
            TokenEncryptionService encryptionService) {
        this.restClient = gmailRestClient;
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
    }

    public synchronized void cacheAccessToken(String token, long expiresInSeconds, Long connectionId) {
        cachedAccessToken = new CachedAccessToken(
                token, Instant.now().plusSeconds(Math.max(0, expiresInSeconds - 60)), connectionId);
    }

    public synchronized String getAccessToken() {
        MailboxConnection connection = connectionRepository.findByProvider(PROVIDER)
                .orElseThrow(() -> new EmailIntegrationException(HttpStatus.CONFLICT, "Gmail is not connected"));
        if (connection.getStatus() == MailboxStatus.NEEDS_RECONNECT) {
            throw reconnectRequired();
        }
        if (cachedAccessToken != null
                && cachedAccessToken.connectionId().equals(connection.getId())
                && Instant.now().isBefore(cachedAccessToken.expiresAt())) {
            return cachedAccessToken.value();
        }
        return refreshAccessToken(connection);
    }

    public synchronized void clearAccessToken() {
        cachedAccessToken = null;
    }

    private String refreshAccessToken(MailboxConnection connection) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getGoogle().getClientId());
        form.add("client_secret", properties.getGoogle().getClientSecret());
        form.add("refresh_token", encryptionService.decrypt(connection.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");

        try {
            JsonNode response = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = requiredText(response, "access_token");
            long expiresIn = response.path("expires_in").asLong(3600);
            cacheAccessToken(accessToken, expiresIn, connection.getId());
            return accessToken;
        } catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            if (responseBody.contains("\"invalid_grant\"")) {
                connection.setStatus(MailboxStatus.NEEDS_RECONNECT);
                connectionRepository.save(connection);
                cachedAccessToken = null;
                throw reconnectRequired();
            }
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google token refresh failed");
        } catch (EmailIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google token refresh failed");
        }
    }

    private static String requiredText(JsonNode response, String field) {
        String value = response == null ? null : response.path(field).asString(null);
        if (value == null || value.isBlank()) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google returned an invalid token response");
        }
        return value;
    }

    private static EmailIntegrationException reconnectRequired() {
        return new EmailIntegrationException(HttpStatus.CONFLICT, "Gmail connection needs to be reconnected");
    }

    private record CachedAccessToken(String value, Instant expiresAt, Long connectionId) {
    }
}
