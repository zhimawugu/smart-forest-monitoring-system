package com.nci.forest.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import java.io.IOException;

/**
 * Resolves the gRPC endpoint through mDNS with fallback to localhost.
 */
public final class GrpcServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceDiscovery.class);

    private static final String DEFAULT_SERVICE_TYPE = "_forest-grpc._tcp.local.";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 50051;
    private static final int DEFAULT_TIMEOUT_MS = 1200;

    private GrpcServiceDiscovery() {
    }

    public static Endpoint resolve() {
        String hostOverride = System.getProperty("grpc.host");
        String portOverride = System.getProperty("grpc.port");
        if (hostOverride != null && !hostOverride.isBlank()) {
            int port = parsePort(portOverride, DEFAULT_PORT);
            logger.info("Using endpoint from system properties: {}:{}", hostOverride, port);
            return new Endpoint(hostOverride, port);
        }

        String serviceType = System.getProperty("grpc.mdns.serviceType", DEFAULT_SERVICE_TYPE);
        int timeoutMs = parseTimeout(System.getProperty("grpc.mdns.timeoutMs"));

        try (JmDNS jmdns = JmDNS.create()) {
            javax.jmdns.ServiceInfo[] services = jmdns.list(serviceType, timeoutMs);
            if (services.length > 0) {
                javax.jmdns.ServiceInfo serviceInfo = services[0];
                String[] hosts = serviceInfo.getHostAddresses();
                String host = hosts.length > 0 ? hosts[0] : serviceInfo.getServer();
                int port = serviceInfo.getPort();
                if (host != null && !host.isBlank() && port > 0) {
                    logger.info("Discovered gRPC endpoint via mDNS: {}:{}", host, port);
                    return new Endpoint(host, port);
                }
            }
            logger.info("No mDNS gRPC service discovered for type={}, fallback to {}:{}",
                    serviceType, DEFAULT_HOST, DEFAULT_PORT);
        } catch (IOException e) {
            logger.warn("mDNS discovery failed, fallback to {}:{} - {}",
                    DEFAULT_HOST, DEFAULT_PORT, e.getMessage());
        }

        return new Endpoint(DEFAULT_HOST, DEFAULT_PORT);
    }

    private static int parsePort(String portText, int fallback) {
        try {
            return portText == null ? fallback : Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int parseTimeout(String timeoutText) {
        try {
            return timeoutText == null ? DEFAULT_TIMEOUT_MS : Integer.parseInt(timeoutText);
        } catch (NumberFormatException ex) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    public static final class Endpoint {
        private final String host;
        private final int port;

        public Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }
    }
}


