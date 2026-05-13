package com.patchmgmt.integration.credential;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.repository.CredentialMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CredentialProviderFactory {

    private final IntegrationProperties integrationProperties;
    private final CredentialMappingRepository credentialMappingRepository;

    @Bean
    public RestTemplate cyberArkRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CredentialProvider credentialProvider(RestTemplate cyberArkRestTemplate) {
        if (integrationProperties.isDemoMode()) {
            log.info("[DEMO MODE] Mock credential provider active — no real credential calls");
            return new MockCredentialProvider();
        }
        if (integrationProperties.getCyberark().isEnabled()) {
            log.info("CyberArk credential provider ACTIVE — fetching from: {}",
                integrationProperties.getCyberark().getBaseUrl());
            return new CyberArkCredentialProvider(integrationProperties, credentialMappingRepository, cyberArkRestTemplate);
        }
        // Local mode: SSH username + AES-encrypted password stored in credential_mappings table
        log.info("[LOCAL MODE] LocalCredentialProvider ACTIVE — SSH credentials read from DB (AES-256 encrypted)");
        return new LocalCredentialProvider(integrationProperties, credentialMappingRepository);
    }
}
