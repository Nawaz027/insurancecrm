package com.example.insurancecrm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
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
