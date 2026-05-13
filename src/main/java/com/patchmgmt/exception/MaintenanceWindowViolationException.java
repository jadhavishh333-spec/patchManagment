package com.patchmgmt.exception;

public class MaintenanceWindowViolationException extends RuntimeException {
    public MaintenanceWindowViolationException(String message) {
        super(message);
    }
}
