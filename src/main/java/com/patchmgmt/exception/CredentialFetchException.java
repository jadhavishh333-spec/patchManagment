package com.patchmgmt.exception;

public class CredentialFetchException extends RuntimeException {
    public CredentialFetchException(String message) {
        super(message);
    }
    public CredentialFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
