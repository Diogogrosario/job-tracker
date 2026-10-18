package com.jobtracker.config.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GmailClientConfiguration {
    @Bean
    public RestClient gmailRestClient() {
        return RestClient.create();
    }
}
