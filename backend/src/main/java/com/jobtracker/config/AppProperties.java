package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Google google = new Google();
    private final Security security = new Security();
    private final Gmail gmail = new Gmail();
    private String frontendUrl = "http://localhost:4200";

    public Google getGoogle() {
        return google;
    }

    public Security getSecurity() {
        return security;
    }

    public Gmail getGmail() {
        return gmail;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public static class Google {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:8080/api/mailbox/oauth/callback";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

    public static class Security {
        private String tokenEncryptionKey = "";

        public String getTokenEncryptionKey() {
            return tokenEncryptionKey;
        }

        public void setTokenEncryptionKey(String tokenEncryptionKey) {
            this.tokenEncryptionKey = tokenEncryptionKey;
        }
    }

    public static class Gmail {
        private String syncQuery;
        private int initialDays = 30;
        private int maxMessagesPerSync = 200;

        public String getSyncQuery() {
            return syncQuery;
        }

        public void setSyncQuery(String syncQuery) {
            this.syncQuery = syncQuery;
        }

        public int getInitialDays() {
            return initialDays;
        }

        public void setInitialDays(int initialDays) {
            this.initialDays = initialDays;
        }

        public int getMaxMessagesPerSync() {
            return maxMessagesPerSync;
        }

        public void setMaxMessagesPerSync(int maxMessagesPerSync) {
            this.maxMessagesPerSync = maxMessagesPerSync;
        }
    }
}
