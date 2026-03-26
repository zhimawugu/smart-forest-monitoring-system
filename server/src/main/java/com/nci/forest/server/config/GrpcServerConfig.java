package com.nci.forest.server.config;

import javax.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

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

    @Value("${mdns.enabled:true}")
    private boolean mdnsEnabled;

    @Value("${mdns.service.type:_forest-grpc._tcp.local.}")
    private String mdnsServiceType;

    @Value("${mdns.service.name:forest-grpc-server}")
    private String mdnsServiceName;

    @Value("${mdns.service.description:Smart Forest gRPC Server}")
    private String mdnsServiceDescription;

    private JmDNS jmDNS;
    private ServiceInfo serviceInfo;

    /**
     * Event listener that triggers when the application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        registerMdnsService();
        logger.info("========================================");
        logger.info("{} started successfully", applicationName);
        logger.info("gRPC Server listening on port: {}", grpcPort);
        logger.info("========================================");
    }

    @PreDestroy
    public void onShutdown() {
        unregisterMdnsService();
    }

    private void registerMdnsService() {
        if (!mdnsEnabled) {
            logger.info("mDNS registration is disabled");
            return;
        }

        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            jmDNS = JmDNS.create(localAddress);

            serviceInfo = ServiceInfo.create(
                    mdnsServiceType,
                    mdnsServiceName,
                    grpcPort,
                    mdnsServiceDescription
            );
            jmDNS.registerService(serviceInfo);

            logger.info("mDNS registered: type={}, name={}, host={}, port={}",
                    mdnsServiceType,
                    mdnsServiceName,
                    localAddress.getHostAddress(),
                    grpcPort);
        } catch (IOException e) {
            logger.warn("Failed to register mDNS service, fallback to static endpoint: {}", e.getMessage());
        }
    }

    private void unregisterMdnsService() {
        if (jmDNS == null) {
            return;
        }

        try {
            if (serviceInfo != null) {
                jmDNS.unregisterService(serviceInfo);
            }
            jmDNS.close();
            logger.info("mDNS service unregistered");
        } catch (IOException e) {
            logger.warn("Failed to close mDNS cleanly: {}", e.getMessage());
        }
    }
}

