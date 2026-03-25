package com.nci.forest.server.service;

import com.nci.forest.proto.*;
import io.grpc.stub.StreamObserver;
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

    // Temperature data storage (sensor_id -> list of latest temperatures)
    private final Map<String, List<TemperatureData>> temperatureData = new ConcurrentHashMap<>();

    /**
     * Add sensor to forest
     */
    public AddSensorResponse addSensor(AddSensorRequest request) {
        logger.info("Adding sensor to forest: {}", request.getForestId());

        String sensorId = UUID.randomUUID().toString();

        Sensor sensor = Sensor.newBuilder()
                .setId(sensorId)
                .setName(request.getName())
                .setForestId(request.getForestId())
                .setLatitude(request.getLocation().getLatitude())
                .setLongitude(request.getLocation().getLongitude())
                .setCreatedAt(System.currentTimeMillis())
                .build();

        sensorsByForest
                .computeIfAbsent(request.getForestId(), k -> new ArrayList<>())
                .add(sensor);

        temperatureData.put(sensorId, new ArrayList<>());

        logger.info("Sensor added successfully: {}", sensorId);

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
        logger.info("Removing sensor: {}", request.getSensorId());

        String sensorId = request.getSensorId();

        // Find and remove sensor from all forests
        boolean found = false;
        for (List<Sensor> sensors : sensorsByForest.values()) {
            if (sensors.removeIf(s -> s.getId().equals(sensorId))) {
                found = true;
                break;
            }
        }

        // Remove temperature data
        temperatureData.remove(sensorId);

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
        logger.info("Listing sensors for forest: {}", request.getForestId());

        List<Sensor> sensors = sensorsByForest.getOrDefault(request.getForestId(), new ArrayList<>());

        return ListSensorsResponse.newBuilder()
                .addAllSensors(sensors)
                .setTotal(sensors.size())
                .build();
    }

    /**
     * Store incoming temperature data
     */
    public void storeTemperatureData(TemperatureData data) {
        logger.debug("Storing temperature data for sensor: {}", data.getSensorId());

        List<TemperatureData> dataList = temperatureData
                .computeIfAbsent(data.getSensorId(), k -> new ArrayList<>());

        // Keep only last 100 readings
        dataList.add(data);
        if (dataList.size() > 100) {
            dataList.remove(0);
        }
    }

    /**
     * Get latest temperature data for sensor
     */
    public List<TemperatureData> getTemperatureData(String sensorId) {
        return new ArrayList<>(temperatureData.getOrDefault(sensorId, new ArrayList<>()));
    }
}

