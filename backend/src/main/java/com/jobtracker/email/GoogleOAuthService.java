package com.jobtracker.email;

import com.jobtracker.config.AppProperties;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class GoogleOAuthService {
    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String PROVIDER = "GMAIL";
    private static final long STATE_LIFETIME_SECONDS = 600;

    private final RestClient restClient;
    private final AppProperties properties;
    private final MailboxConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;
    private final GmailTokenService tokenService;
    private final GmailApiClient gmailApiClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> pendingStates = new ConcurrentHashMap<>();

    public GoogleOAuthService(
            RestClient gmailRestClient,
            AppProperties properties,
            MailboxConnectionRepository connectionRepository,
            TokenEncryptionService encryptionService,
            GmailTokenService tokenService,
            GmailApiClient gmailApiClient) {
        this.restClient = gmailRestClient;
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.tokenService = tokenService;
        this.gmailApiClient = gmailApiClient;
    }

    public URI createAuthorizationUri() {
        requireOAuthConfiguration();
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        pendingStates.put(state, now.plusSeconds(STATE_LIFETIME_SECONDS));

        return UriComponentsBuilder.fromUriString(AUTH_URI)
                .queryParam("client_id", properties.getGoogle().getClientId())
                .queryParam("redirect_uri", properties.getGoogle().getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    public void handleCallback(String code, String state, String providerError) {
        if (state == null) {
            throw new EmailIntegrationException(HttpStatus.BAD_REQUEST, "Invalid OAuth state");
        }
        Instant expiresAt = pendingStates.remove(state);
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new EmailIntegrationException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state");
        }
        if (providerError != null) {
            throw new EmailIntegrationException(HttpStatus.BAD_REQUEST, "Google authorization was not completed");
        }
        if (code == null || code.isBlank()) {
            throw new EmailIntegrationException(HttpStatus.BAD_REQUEST, "Google authorization code is missing");
        }
        requireOAuthConfiguration();

        JsonNode tokens = exchangeAuthorizationCode(code);
        String accessToken = requiredText(tokens, "access_token");
        String refreshToken = requiredText(tokens, "refresh_token");
        String email = gmailApiClient.getAccountEmail(accessToken);

        MailboxConnection connection = connectionRepository.findByProvider(PROVIDER).orElseGet(MailboxConnection::new);
        connection.setProvider(PROVIDER);
        connection.setEmail(email);
        connection.setEncryptedRefreshToken(encryptionService.encrypt(refreshToken));
        connection.setStatus(MailboxStatus.CONNECTED);
        connection.setLastSyncAt(null);
        connection.setLastSyncStatus(null);
        connection.setLastSyncSummary(null);
        connection = connectionRepository.save(connection);
        tokenService.cacheAccessToken(accessToken, tokens.path("expires_in").asLong(3600), connection.getId());
    }

    private JsonNode exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getGoogle().getClientId());
        form.add("client_secret", properties.getGoogle().getClientSecret());
        form.add("redirect_uri", properties.getGoogle().getRedirectUri());
        form.add("grant_type", "authorization_code");
        try {
            return restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google authorization token exchange failed");
        }
    }

    private void requireOAuthConfiguration() {
        if (properties.getGoogle().getClientId().isBlank()
                || properties.getGoogle().getClientSecret().isBlank()) {
            throw new EmailIntegrationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google OAuth client credentials are not configured");
        }
    }

    private static String requiredText(JsonNode response, String field) {
        String value = response == null ? null : response.path(field).asString(null);
        if (value == null || value.isBlank()) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google returned an invalid token response");
        }
        return value;
    }
}
