package com.nci.forest.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;

/**
 * gRPC Server Configuration
 * Handles server startup events and configurations
 */
@Configuration
public class GrpcServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Event listener that triggers when the application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("========================================");
        logger.info("{} started successfully", applicationName);
        logger.info("gRPC Server listening on port: {}", grpcPort);
        logger.info("========================================");
    }
}

