package com.nci.forest.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import java.io.IOException;

/**
 * Resolves the gRPC endpoint through jmDNS
 */
public final class GrpcServiceDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceDiscovery.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 50051;
    private static final int DEFAULT_TIMEOUT_MS = 1200;

    private GrpcServiceDiscovery() {
    }

    /**
     * Resolve gRPC endpoint with custom service type
     */
    public static Endpoint resolve(String serviceType) {
        try (JmDNS jmdns = JmDNS.create()) {
            javax.jmdns.ServiceInfo[] services = jmdns.list(serviceType, DEFAULT_TIMEOUT_MS);
            if (services.length > 0) {
                javax.jmdns.ServiceInfo serviceInfo = services[0];
                String[] hosts = serviceInfo.getHostAddresses();
                String host = hosts.length > 0 ? hosts[0] : serviceInfo.getServer();
                int port = serviceInfo.getPort();
                if (host != null && !host.isBlank() && port > 0) {
                    return new Endpoint(host, port);
                }
            }
        } catch (IOException e) {
            logger.warn("mDNS discovery failed, fallback to {}:{} - {}",
                    DEFAULT_HOST, DEFAULT_PORT, e.getMessage());
        }

        return new Endpoint(DEFAULT_HOST, DEFAULT_PORT);
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
