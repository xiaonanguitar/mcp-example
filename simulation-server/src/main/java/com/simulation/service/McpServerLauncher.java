package com.simulation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

@Service
public class McpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(McpServerLauncher.class);

    @Value("${simulation.mcp.enabled:true}")
    private boolean mcpEnabled;

    @Value("${simulation.mcp.port:3000}")
    private int mcpPort;

    @Value("${server.port:8080}")
    private int javaPort;

    private Process mcpProcess;

    @EventListener(ApplicationReadyEvent.class)
    public void startMcpServer() {
        if (!mcpEnabled) {
            log.info("MCP server disabled by configuration");
            return;
        }

        try {
            Path mcpDir = Paths.get(System.getProperty("user.dir"), "..", "simulation-mcp");
            if (!Files.exists(mcpDir)) {
                mcpDir = Paths.get("E:/Code/MCP_Example/simulation-mcp");
            }

            String[] pythonCmd = {"py", "-3.12"};
            Path serverScript = mcpDir.resolve("server.py");

            if (!Files.exists(serverScript)) {
                log.warn("MCP server script not found at: {}", serverScript);
                return;
            }

            log.info("Starting MCP server on port {}...", mcpPort);
            log.info("MCP server directory: {}", mcpDir.toAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(
                pythonCmd[0], pythonCmd[1],
                serverScript.toString(),
                "--port", String.valueOf(mcpPort),
                "--java-url", "http://localhost:" + javaPort
            );
            pb.directory(mcpDir.toFile());
            pb.redirectErrorStream(true);

            mcpProcess = pb.start();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(mcpProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[MCP] {}", line);
                    }
                } catch (IOException e) {
                    log.error("Error reading MCP server output", e);
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();

            Runtime.getRuntime().addShutdownHook(new Thread(this::stopMcpServer));

            log.info("MCP server started successfully on port {}", mcpPort);

        } catch (Exception e) {
            log.error("Failed to start MCP server", e);
        }
    }

    public void stopMcpServer() {
        if (mcpProcess != null && mcpProcess.isAlive()) {
            log.info("Stopping MCP server...");
            mcpProcess.destroy();
            try {
                if (!mcpProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    mcpProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                mcpProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("MCP server stopped");
        }
    }
}
