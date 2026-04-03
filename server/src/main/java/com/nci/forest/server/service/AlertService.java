package com.nci.forest.server.service;

import com.nci.forest.proto.AlertConfig;
import com.nci.forest.proto.AlertEvent;
import com.nci.forest.proto.SetAlertRequest;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alert Service Implementation
 * Manages alert thresholds and triggers alerts when temperature exceeds limits
 */
@Service
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    // Store alert configurations for each sensor: sensor_id -> AlertConfig
    private final ConcurrentHashMap<String, AlertConfig> alertConfigs = new ConcurrentHashMap<>();

    // Store active alert observers: client_id -> StreamObserver<AlertEvent>
    private final ConcurrentHashMap<String, StreamObserver<AlertEvent>> alertObservers = new ConcurrentHashMap<>();

    /**
     * Register client observer
     */
    public void watchAlerts(String clientId, StreamObserver<AlertEvent> responseObserver) {
        alertObservers.put(clientId, responseObserver);
    }

    /**
     * Process SetAlertRequest from client
     */
    public void setAlertThreshold(SetAlertRequest request) {
        AlertConfig config = AlertConfig.newBuilder().setSensorId(request.getSensorId()).setMaxTemperature(request.getMaxTemperature()).build();
        alertConfigs.put(request.getSensorId(), config);
    }

    /**
     * Unregister client observer
     */
    public void unregisterClient(String clientId) {
        alertObservers.remove(clientId);
    }

    /**
     * Check if temperature exceeds alert threshold and trigger alert if needed
     */
    public void checkAndTriggerAlert(String sensorId, String sensorName, String forestId, double currentTemperature) {
        AlertConfig config = alertConfigs.get(sensorId);
        if (config == null) {
            return;
        }

        if (currentTemperature > config.getMaxTemperature()) {
            AlertEvent alertEvent = AlertEvent.newBuilder().setAlertId(UUID.randomUUID().toString()).setSensorId(sensorId).setSensorName(sensorName).setForestId(forestId).setCurrentTemperature(Math.round(currentTemperature * 100.0) / 100.0).setThreshold(config.getMaxTemperature()).setTimestamp(System.currentTimeMillis()).setAlertType("OVER_TEMP").build();

            for (Map.Entry<String, StreamObserver<AlertEvent>> entry : alertObservers.entrySet()) {
                try {
                    entry.getValue().onNext(alertEvent);
                } catch (Exception e) {
                    logger.error("Failed to send alert to client {}: {}", entry.getKey(), e.getMessage());
                    alertObservers.remove(entry.getKey());
                }
            }
        }
    }
}
