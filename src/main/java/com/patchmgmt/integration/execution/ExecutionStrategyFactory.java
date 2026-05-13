package com.patchmgmt.integration.execution;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Factory that wires the correct execution strategy based on config.
 * Rules:
 *  - demo-mode=true → MockExecutionStrategy for all servers
 *  - winrm.enabled=true + WINDOWS server → WinRmExecutionStrategy
 *  - ssh.enabled=true  + LINUX server   → SshExecutionStrategy
 *  - fallback → MockExecutionStrategy
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ExecutionStrategyFactory {

    private final IntegrationProperties props;

    @Bean
    public RemoteExecutionStrategy remoteExecutionStrategy() {
        if (props.isDemoMode()) {
            log.info("[DEMO MODE] Using MockExecutionStrategy — no real remote connections");
            return new MockExecutionStrategy();
        }
        // Return a composite dispatcher that picks the right strategy per server
        log.info("Production execution: WinRM={}, SSH={}", props.getWinrm().isEnabled(), props.getSsh().isEnabled());
        return new DispatchingExecutionStrategy(props);
    }

    /**
     * Dispatcher that selects WinRM or SSH based on the server OS at runtime.
     */
    static class DispatchingExecutionStrategy implements RemoteExecutionStrategy {
        private final WinRmExecutionStrategy winRm;
        private final SshExecutionStrategy ssh;
        private final MockExecutionStrategy mock;

        DispatchingExecutionStrategy(IntegrationProperties props) {
            this.winRm = new WinRmExecutionStrategy(props);
            this.ssh   = new SshExecutionStrategy(props);
            this.mock  = new MockExecutionStrategy();
        }

        @Override
        public boolean supports(Server server) { return true; }

        @Override
        public com.patchmgmt.integration.model.ExecutionResult execute(
                Server server, String command,
                com.patchmgmt.integration.model.ResolvedCredential credential) {
            if (server.getOsType() == OsType.WINDOWS && winRm.supports(server)) {
                return winRm.execute(server, command, credential);
            } else if (server.getOsType() == OsType.LINUX && ssh.supports(server)) {
                return ssh.execute(server, command, credential);
            }
            return mock.execute(server, command, credential);
        }

        @Override
        public com.patchmgmt.integration.model.ExecutionResult executeWithFile(
                Server server, String localFilePath, String remoteFilePath,
                String installCommand, com.patchmgmt.integration.model.ResolvedCredential credential) {
            if (server.getOsType() == OsType.WINDOWS && winRm.supports(server)) {
                return winRm.executeWithFile(server, localFilePath, remoteFilePath, installCommand, credential);
            } else if (server.getOsType() == OsType.LINUX && ssh.supports(server)) {
                return ssh.executeWithFile(server, localFilePath, remoteFilePath, installCommand, credential);
            }
            return mock.executeWithFile(server, localFilePath, remoteFilePath, installCommand, credential);
        }
    }
}
