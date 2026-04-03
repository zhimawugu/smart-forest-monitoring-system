package com.nci.forest.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

/**
 * gRPC Server Configuration
 * Handles server startup events and configurations
 */
@Configuration
public class GrpcServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Value("${jmdns.service.type:_forest-grpc._tcp.local.}")
    private String jmdnsServiceType;

    @Value("${jmdns.service.name:forest-grpc-server}")
    private String jmdnsServiceName;

    @Value("${jmdns.service.description:Smart Forest gRPC Server}")
    private String jmdnsServiceDescription;

    private JmDNS jmDNS;
    private ServiceInfo serviceInfo;

    /**
     * Event listener that triggers when the application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        registerMdnsService();
    }

    @PreDestroy
    public void onShutdown() {
        unregisterJMdnsService();
    }

    private void registerMdnsService() {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            jmDNS = JmDNS.create(localAddress);

            serviceInfo = ServiceInfo.create(jmdnsServiceType, jmdnsServiceName, grpcPort, jmdnsServiceDescription);
            jmDNS.registerService(serviceInfo);
        } catch (IOException e) {
            logger.warn("Failed to register mDNS service, fallback to static endpoint: {}", e.getMessage());
        }
    }

    private void unregisterJMdnsService() {
        if (jmDNS == null) {
            return;
        }

        try {
            if (serviceInfo != null) {
                jmDNS.unregisterService(serviceInfo);
            }
            jmDNS.close();
        } catch (IOException e) {
            logger.warn("Failed to close jmDNS cleanly: {}", e.getMessage());
        }
    }
}
