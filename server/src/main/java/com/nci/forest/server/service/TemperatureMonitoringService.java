package com.nci.forest.server.service;

import com.nci.forest.proto.TemperatureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temperature Monitoring Service
 * Monitors temperature data and triggers alerts when thresholds are exceeded
 */
@Service
public class TemperatureMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureMonitoringService.class);

    @Autowired
    private AlertService alertService;

    @Autowired
    private SensorService sensorService;

    // Store latest temperature data for each sensor: sensor_id -> TemperatureData
    private final ConcurrentHashMap<String, TemperatureData> latestTemperatureData = new ConcurrentHashMap<>();

    /**
     * Record temperature data point
     */
    public void recordTemperatureData(TemperatureData temperatureData) {
        String sensorId = temperatureData.getSensorId();

        logger.debug("Recording temperature data: sensor={}, temp={}",
                    sensorId, temperatureData.getTemperature());

        // Store the latest temperature
        latestTemperatureData.put(sensorId, temperatureData);

        // Check if alert should be triggered
        alertService.checkAndTriggerAlert(
                temperatureData.getSensorId(),
                temperatureData.getSensorName(),
                temperatureData.getForestId(),
                temperatureData.getTemperature()
        );
    }

    /**
     * Get latest temperature for a sensor
     */
    public TemperatureData getLatestTemperature(String sensorId) {
        return latestTemperatureData.get(sensorId);
    }

    /**
     * Get all latest temperatures
     */
    public Map<String, TemperatureData> getAllLatestTemperatures() {
        return new HashMap<>(latestTemperatureData);
    }
}

