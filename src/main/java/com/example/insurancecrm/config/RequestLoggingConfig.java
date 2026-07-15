package com.example.insurancecrm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public CredentialRedactingRequestLoggingFilter requestLoggingFilter() {
        CredentialRedactingRequestLoggingFilter filter = new CredentialRedactingRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(1000);
        filter.setIncludeHeaders(false); // avoid logging JWT tokens
        filter.setIncludeClientInfo(false);
        filter.setBeforeMessagePrefix("→ REQUEST: ");
        filter.setAfterMessagePrefix("← RESPONSE: ");
        return filter;
    }
}
