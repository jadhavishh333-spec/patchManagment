package com.patchmgmt.integration.credential;

import com.patchmgmt.entity.Server;
import com.patchmgmt.integration.model.ResolvedCredential;

/**
 * Strategy interface for fetching server credentials.
 * Implementations: CyberArkCredentialProvider, MockCredentialProvider.
 */
public interface CredentialProvider {
    ResolvedCredential fetchCredential(Server server);
}
