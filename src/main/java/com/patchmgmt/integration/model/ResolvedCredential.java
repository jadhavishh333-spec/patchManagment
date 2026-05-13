package com.patchmgmt.integration.model;

import java.util.Arrays;

/**
 * Holds resolved credentials fetched from CyberArk or mock.
 * Uses char[] for password to allow explicit clearing from memory — avoids String pool retention.
 */
public record ResolvedCredential(String username, char[] password) {

    /** Returns password as String only when strictly needed for protocol calls. Clear after use. */
    public String passwordAsString() {
        return new String(password);
    }

    /** Zeroes out the password array to minimise in-memory exposure. */
    public void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
