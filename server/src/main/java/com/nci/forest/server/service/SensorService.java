package com.nci.forest.server.service;

import com.nci.forest.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sensor Service Implementation
 */
@Service
public class SensorService {

    private static final Logger logger = LoggerFactory.getLogger(SensorService.class);

    // In-memory storage for sensors (forest_id -> list of sensors)
    private final Map<String, List<Sensor>> sensorsByForest = new ConcurrentHashMap<>();

    /**
     * Add sensor to forest
     */
    public AddSensorResponse addSensor(AddSensorRequest request) {
        String sensorId = UUID.randomUUID().toString();

        Sensor sensor = Sensor.newBuilder()
                .setId(sensorId)
                .setName(request.getName())
                .setForestId(request.getForestId())
                .setLatitude(request.getLatitude())
                .setLongitude(request.getLongitude())
                .setCreatedAt(System.currentTimeMillis())
                .build();

        sensorsByForest
                .computeIfAbsent(request.getForestId(), k -> new ArrayList<>())
                .add(sensor);

        return AddSensorResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Sensor added successfully")
                .setSensor(sensor)
                .build();
    }

    /**
     * Remove sensor from forest
     */
    public RemoveSensorResponse removeSensor(RemoveSensorRequest request) {
        String sensorId = request.getSensorId();

        // Find and remove sensor from all forests
        boolean found = false;
        for (List<Sensor> sensors : sensorsByForest.values()) {
            if (sensors.removeIf(s -> s.getId().equals(sensorId))) {
                found = true;
                break;
            }
        }

        if (found) {
            logger.info("Sensor removed successfully: {}", sensorId);
            return RemoveSensorResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Sensor removed successfully")
                    .build();
        } else {
            logger.warn("Sensor not found: {}", sensorId);
            return RemoveSensorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Sensor not found")
                    .build();
        }
    }

    /**
     * List all sensors in a forest
     */
    public ListSensorsResponse listSensors(ListSensorsRequest request) {
        List<Sensor> sensors = sensorsByForest.getOrDefault(request.getForestId(), new ArrayList<>());

        return ListSensorsResponse.newBuilder()
                .addAllSensors(sensors)
                .setTotal(sensors.size())
                .build();
    }
}
