package com.patchmgmt.integration.model;

/**
 * Result of a remote command execution (WinRM / SSH / Mock).
 */
public record ExecutionResult(
    boolean success,
    String output,
    String error,
    int exitCode
) {
    public static ExecutionResult success(String output) {
        return new ExecutionResult(true, output, null, 0);
    }

    public static ExecutionResult failure(String error, int exitCode) {
        return new ExecutionResult(false, null, error, exitCode);
    }

    public static ExecutionResult failure(String error) {
        return new ExecutionResult(false, null, error, 1);
    }
}
