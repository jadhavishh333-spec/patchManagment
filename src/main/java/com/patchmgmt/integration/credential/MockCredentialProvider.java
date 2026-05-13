package com.patchmgmt.integration.credential;

import com.patchmgmt.entity.Server;
import com.patchmgmt.integration.model.ResolvedCredential;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock credential provider for demo/test mode.
 * Returns predictable fake credentials — safe for local testing with no CyberArk infrastructure.
 */
@Slf4j
public class MockCredentialProvider implements CredentialProvider {

    @Override
    public ResolvedCredential fetchCredential(Server server) {
        log.debug("[DEMO MODE] MockCredentialProvider returning fake credential for server: {}", server.getName());
        String username = server.getOsType() != null && server.getOsType().name().equals("WINDOWS")
            ? "Administrator"
            : "root";
        // In real CyberArk, password is never in code — this is for demo only
        return new ResolvedCredential(username, "DemoPassword123!".toCharArray());
    }
}
