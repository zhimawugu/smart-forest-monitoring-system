package com.nci.forest.server.service;

import com.nci.forest.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

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
     * Register client observer and handle incoming alert messages
     */
    public void watchAlerts(String clientId, StreamObserver<AlertEvent> responseObserver) {
        logger.info("Client {} watching alerts", clientId);

        // Store the observer for this client
        alertObservers.put(clientId, responseObserver);
        logger.info("Total active alert observers: {}", alertObservers.size());
    }

    /**
     * Process SetAlertRequest from client
     */
    public void setAlertThreshold(SetAlertRequest request) {
        String sensorId = request.getSensorId();
        double maxTemp = request.getMaxTemperature();

        logger.info("Setting alert threshold for sensor {}: max={}", sensorId, maxTemp);

        // Store the alert configuration
        AlertConfig config = AlertConfig.newBuilder()
                .setSensorId(sensorId)
                .setMaxTemperature(maxTemp)
                .build();

        alertConfigs.put(sensorId, config);
        logger.info("Alert config stored. Total configs: {}", alertConfigs.size());
    }

    /**
     * Unregister client observer
     */
    public void unregisterClient(String clientId) {
        logger.info("Client {} unregistered from alerts", clientId);
        alertObservers.remove(clientId);
    }

    /**
     * Check if temperature exceeds alert threshold and trigger alert if needed
     */
    public void checkAndTriggerAlert(String sensorId, String sensorName, String forestId,
                                     double currentTemperature) {
        logger.debug("Checking alert for sensor {}: temp={}", sensorId, currentTemperature);

        AlertConfig config = alertConfigs.get(sensorId);
        if (config == null) {
            // No alert configured for this sensor
            logger.debug("No alert config found for sensor: {}", sensorId);
            return;
        }

        logger.debug("Alert config found for sensor {}: maxTemp={}", sensorId, config.getMaxTemperature());

        // Only check if temperature exceeds max threshold
        if (currentTemperature > config.getMaxTemperature()) {
            // Create alert event
            String alertId = UUID.randomUUID().toString();
            AlertEvent alertEvent = AlertEvent.newBuilder()
                    .setAlertId(alertId)
                    .setSensorId(sensorId)
                    .setSensorName(sensorName)
                    .setForestId(forestId)
                    .setCurrentTemperature(Math.round(currentTemperature * 100.0) / 100.0)
                    .setThreshold(config.getMaxTemperature())
                    .setTimestamp(System.currentTimeMillis())
                    .setAlertType("OVER_TEMP")
                    .build();

            logger.warn("Alert triggered: sensor={}, type=OVER_TEMP, temp={}, threshold={}",
                       sensorId, currentTemperature, config.getMaxTemperature());

            // Push alert to all connected clients
            logger.info("Pushing alert to {} connected clients", alertObservers.size());
            for (Map.Entry<String, StreamObserver<AlertEvent>> entry : alertObservers.entrySet()) {
                try {
                    entry.getValue().onNext(alertEvent);
                    logger.info("Alert sent to client: {}", entry.getKey());
                } catch (Exception e) {
                    logger.error("Failed to send alert to client {}: {}", entry.getKey(), e.getMessage());
                    alertObservers.remove(entry.getKey());
                }
            }
        }
    }
}
