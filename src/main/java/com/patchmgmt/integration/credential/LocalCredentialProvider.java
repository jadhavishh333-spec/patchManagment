package com.patchmgmt.integration.credential;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.entity.CredentialMapping;
import com.patchmgmt.entity.Server;
import com.patchmgmt.exception.CredentialFetchException;
import com.patchmgmt.integration.model.ResolvedCredential;
import com.patchmgmt.repository.CredentialMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev/test credential provider that reads SSH username + AES-256-encrypted password
 * directly from the credential_mappings table (columns cyberark_safe / cyberark_object).
 *
 * Activated when: integration.demo-mode=false AND integration.cyberark.enabled=false
 *
 * Storage layout (repurposed columns):
 *   cyberark_safe   → SSH username
 *   cyberark_object → AES-256-GCM encrypted SSH password (Base64)
 */
@Slf4j
@RequiredArgsConstructor
public class LocalCredentialProvider implements CredentialProvider {

    private final IntegrationProperties props;
    private final CredentialMappingRepository credentialMappingRepository;

    @Override
    public ResolvedCredential fetchCredential(Server server) {
        log.info("[LOCAL MODE] Fetching local SSH credentials for server: {} ({})",
                 server.getName(), server.getIpAddress());

        CredentialMapping mapping = credentialMappingRepository.findByServer_Id(server.getId())
            .orElseThrow(() -> new CredentialFetchException(
                "No credential mapping found for server: " + server.getName()
                + ". Go to Admin → Credentials and create a mapping with SSH username/password."));

        // cyberark_safe column repurposed to store SSH username
        String username = mapping.getCyberArkSafe();

        // cyberark_object column repurposed to store AES-encrypted SSH password
        String encryptedPassword = mapping.getCyberArkObject();

        if (username == null || username.isBlank()) {
            throw new CredentialFetchException(
                "SSH username is missing in credential mapping for server: " + server.getName());
        }
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new CredentialFetchException(
                "SSH password is missing in credential mapping for server: " + server.getName());
        }

        try {
            String encKey  = props.getLocalCredentials().getEncryptionKey();
            String password = AesEncryptionUtil.decrypt(encryptedPassword, encKey);
            log.info("[LOCAL MODE] Credentials resolved for user '{}' on server '{}'",
                     username, server.getName());
            return new ResolvedCredential(username, password.toCharArray());
        } catch (Exception e) {
            log.error("[LOCAL MODE] Failed to decrypt SSH password for server {}: {}",
                      server.getName(), e.getMessage());
            throw new CredentialFetchException(
                "Could not decrypt SSH password for server: " + server.getName()
                + ". Ensure encryption key in application.yml matches the key used when saving.", e);
        }
    }
}
