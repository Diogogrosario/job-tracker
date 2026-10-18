package com.jobtracker.email.mailbox;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class GmailApiClient {
    private static final String API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final RestClient restClient;

    public GmailApiClient(RestClient gmailRestClient) {
        this.restClient = gmailRestClient;
    }

    public String getAccountEmail(String accessToken) {
        JsonNode response = getJson(API_BASE + "/profile", accessToken);
        String email = response.path("emailAddress").asString(null);
        if (email == null || email.isBlank()) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Google did not return a Gmail address");
        }
        return email;
    }

    public MessagePage listMessages(String accessToken, String query, int maxResults, String pageToken) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE + "/messages")
                .queryParam("q", query)
                .queryParam("maxResults", maxResults);
        if (pageToken != null) {
            builder.queryParam("pageToken", pageToken);
        }
        JsonNode response = getJson(builder.build().encode().toUri(), accessToken);
        List<String> ids = new ArrayList<>();
        JsonNode messages = response.path("messages");
        if (messages.isArray()) {
            for (JsonNode message : messages) {
                String id = message.path("id").asString(null);
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        String nextPageToken = response.path("nextPageToken").asString(null);
        return new MessagePage(ids, nextPageToken);
    }

    public JsonNode getMessage(String accessToken, String messageId) {
        URI uri = UriComponentsBuilder.fromUriString(API_BASE + "/messages/{id}")
                .queryParam("format", "full")
                .buildAndExpand(messageId)
                .encode()
                .toUri();
        return getJson(uri, accessToken);
    }

    private JsonNode getJson(String uri, String accessToken) {
        return getJson(URI.create(uri), accessToken);
    }

    private JsonNode getJson(URI uri, String accessToken) {
        try {
            return restClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new EmailIntegrationException(HttpStatus.BAD_GATEWAY, "Gmail API request failed");
        }
    }

    public record MessagePage(List<String> ids, String nextPageToken) {
    }
}
