package com.patchmgmt.integration.execution;

import com.patchmgmt.entity.Server;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;

/**
 * Strategy interface for remote patch command execution.
 * Implementations: WinRmExecutionStrategy, SshExecutionStrategy, MockExecutionStrategy.
 *
 * executeWithFile() supports manually-uploaded patch binaries:
 *   1. Transfers localFilePath → remoteFilePath on the target server.
 *   2. Runs installCommand (which references remoteFilePath).
 */
public interface RemoteExecutionStrategy {

    ExecutionResult execute(Server server, String command, ResolvedCredential credential);

    boolean supports(Server server);

    /**
     * Transfer a local patch binary to the remote server and then run the
     * provided install command.
     *
     * @param server         target server
     * @param localFilePath  absolute path of the patch file on THIS server
     * @param remoteFilePath absolute path where the file should land on the TARGET
     * @param installCommand command to run on the target after the file arrives
     * @param credential     resolved credentials for the target
     */
    default ExecutionResult executeWithFile(Server server,
                                            String localFilePath,
                                            String remoteFilePath,
                                            String installCommand,
                                            ResolvedCredential credential) {
        return ExecutionResult.failure(
            "File-based execution is not supported by strategy: " + getClass().getSimpleName());
    }
}
