package com.jobtracker.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Ollama ollama = new Ollama();
    private final Processing processing = new Processing();
    private final Google google = new Google();
    private final Security security = new Security();
    private final Gmail gmail = new Gmail();
    private String frontendUrl = "http://localhost:4200";

    public Ollama getOllama() {
        return ollama;
    }

    public Processing getProcessing() {
        return processing;
    }

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

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "gemma3:4b";
        private String keepAlive = "30s";
        private int timeoutSeconds = 180;
        private int maxBodyChars = 3000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(String keepAlive) {
            this.keepAlive = keepAlive;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxBodyChars() {
            return maxBodyChars;
        }

        public void setMaxBodyChars(int maxBodyChars) {
            this.maxBodyChars = maxBodyChars;
        }
    }

    public static class Processing {
        private int maxPerRun = 50;
        private boolean autoApplyMatched = true;
        private boolean autoApplyAiOnly;
        private boolean requireAgreement = true;

        public int getMaxPerRun() {
            return maxPerRun;
        }

        public void setMaxPerRun(int maxPerRun) {
            this.maxPerRun = maxPerRun;
        }

        public boolean isAutoApplyMatched() {
            return autoApplyMatched;
        }

        public void setAutoApplyMatched(boolean autoApplyMatched) {
            this.autoApplyMatched = autoApplyMatched;
        }

        public boolean isAutoApplyAiOnly() {
            return autoApplyAiOnly;
        }

        public void setAutoApplyAiOnly(boolean autoApplyAiOnly) {
            this.autoApplyAiOnly = autoApplyAiOnly;
        }

        public boolean isRequireAgreement() {
            return requireAgreement;
        }

        public void setRequireAgreement(boolean requireAgreement) {
            this.requireAgreement = requireAgreement;
        }
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
        private int minSyncIntervalMinutes = 5;

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

        public int getMinSyncIntervalMinutes() {
            return minSyncIntervalMinutes;
        }

        public void setMinSyncIntervalMinutes(int minSyncIntervalMinutes) {
            this.minSyncIntervalMinutes = minSyncIntervalMinutes;
        }
    }
}
