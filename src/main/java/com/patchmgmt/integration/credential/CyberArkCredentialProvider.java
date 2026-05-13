package com.patchmgmt.integration.credential;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.entity.CredentialMapping;
import com.patchmgmt.entity.Server;
import com.patchmgmt.exception.CredentialFetchException;
import com.patchmgmt.integration.model.ResolvedCredential;
import com.patchmgmt.repository.CredentialMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

/**
 * Fetches credentials from CyberArk AIM REST API.
 * Used when integration.cyberark.enabled=true.
 */
@Slf4j
@RequiredArgsConstructor
public class CyberArkCredentialProvider implements CredentialProvider {

    private final IntegrationProperties props;
    private final CredentialMappingRepository credentialMappingRepository;
    private final RestTemplate restTemplate;

    @Override
    public ResolvedCredential fetchCredential(Server server) {
        log.info("Fetching CyberArk credential for server: {} ({})", server.getName(), server.getIpAddress());

        // Look up credential mapping for this server
        CredentialMapping mapping = credentialMappingRepository.findByServer_Id(server.getId())
            .orElseThrow(() -> new CredentialFetchException(
                "No CyberArk credential mapping found for server: " + server.getName()));

        String safe   = mapping.getCyberArkSafe();
        String object = mapping.getCyberArkObject();
        String appId  = props.getCyberark().getAppId();

        String url = props.getCyberark().getBaseUrl()
            + "?AppID=" + appId
            + "&Safe=" + safe
            + "&Object=" + object;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                String username = (String) body.get("UserName");
                String password = (String) body.get("Content");
                if (username == null || password == null) {
                    throw new CredentialFetchException("CyberArk response missing UserName or Content");
                }
                log.info("CyberArk credential fetched successfully for server: {}", server.getName());
                return new ResolvedCredential(username, password.toCharArray());
            } else {
                throw new CredentialFetchException("CyberArk returned non-OK response: " + response.getStatusCode());
            }
        } catch (CredentialFetchException e) {
            throw e;
        } catch (Exception e) {
            log.error("CyberArk fetch failed for server {}: {}", server.getName(), e.getMessage());
            throw new CredentialFetchException("CyberArk fetch failed: " + e.getMessage(), e);
        }
    }
}
