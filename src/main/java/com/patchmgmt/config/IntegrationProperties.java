package com.patchmgmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "integration")
@Data
public class IntegrationProperties {

    private boolean demoMode = true;
    private CyberArk cyberark = new CyberArk();
    private WinRm winrm = new WinRm();
    private Ssh ssh = new Ssh();
    private LocalCredentials localCredentials = new LocalCredentials();

    @Data
    public static class CyberArk {
        private boolean enabled = false;
        private String baseUrl;
        private String appId;
        private String safe;
        private int connectionTimeoutSeconds = 10;
        private int readTimeoutSeconds = 15;
        private boolean verifySsl = false;
    }

    @Data
    public static class WinRm {
        private boolean enabled = false;
        private int port = 5985;
        private boolean useHttps = false;
        private int timeoutSeconds = 120;
        private String authScheme = "basic";
    }

    @Data
    public static class Ssh {
        private boolean enabled = false;
        private int port = 22;
        private int timeoutSeconds = 120;
        private boolean knownHostsCheck = false;
        private boolean strictHostKeyChecking = false;
    }

    /**
     * Used when demo-mode=false AND cyberark.enabled=false.
     * SSH credentials are stored AES-256-GCM encrypted in the credential_mappings table.
     * Suitable for dev/test only — use CyberArk for production.
     */
    @Data
    public static class LocalCredentials {
        /** Any passphrase — SHA-256 hashed to 256-bit AES key. Keep secret. */
        private String encryptionKey = "PatchMgmt-DefaultKey-ChangeMe!";
    }
}
