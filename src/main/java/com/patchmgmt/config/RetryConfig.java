package com.patchmgmt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // Retry behaviour is configured via @Retryable annotations on service methods.
    // Global defaults can be set here if needed.
}
