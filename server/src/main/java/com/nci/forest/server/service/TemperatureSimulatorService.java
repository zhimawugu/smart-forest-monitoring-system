package com.nci.forest.server.service;

import com.nci.forest.proto.TemperatureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Temperature Simulator Service
 * Simulates real-time temperature data streaming for sensors based on actual sensors in SensorService
 */
@Service
public class TemperatureSimulatorService {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureSimulatorService.class);

    private static final Random random = new Random();
    private static final double TEMPERATURE_VARIANCE = 2.0; // ±2°C variance
    private static final double DEFAULT_BASE_TEMPERATURE = 22.0; // Default base temperature

    // Base temperature for each sensor (sensor_id -> base_temp)
    private final ConcurrentHashMap<String, Double> baseTemperatures = new ConcurrentHashMap<>();

    // Subscribers for each sensor: sensor_id -> list of callbacks
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<TemperatureData>>> subscribers
            = new ConcurrentHashMap<>();

    // Simulator threads for each sensor
    private final ConcurrentHashMap<String, Thread> simulatorThreads = new ConcurrentHashMap<>();

    // Thread-safe flag for running simulators
    private volatile boolean isRunning = true;

    @Autowired
    private SensorService sensorService;

    @Autowired
    private AlertService alertService;

    /**
     * Subscribe to temperature stream for a specific sensor
     */
    public void subscribe(String sensorId, String sensorName, String forestId, Consumer<TemperatureData> callback) {
        logger.info("New subscriber for sensor: {} ({})", sensorId, sensorName);

        // Add callback to subscribers list
        subscribers.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(callback);

        // Start simulator thread for this sensor if not already running
        if (!simulatorThreads.containsKey(sensorId)) {
            startSimulator(sensorId, sensorName, forestId);
        }
    }

    /**
     * Unsubscribe from temperature stream
     */
    public void unsubscribe(String sensorId, Consumer<TemperatureData> callback) {
        CopyOnWriteArrayList<Consumer<TemperatureData>> subs = subscribers.get(sensorId);
        if (subs != null) {
            subs.remove(callback);
            logger.info("Unsubscribed from sensor: {}, remaining subscribers: {}", sensorId, subs.size());

            // Stop simulator if no more subscribers
            if (subs.isEmpty()) {
                stopSimulator(sensorId);
            }
        }
    }

    /**
     * Start temperature simulator thread for a sensor
     */
    private void startSimulator(String sensorId, String sensorName, String forestId) {
        Thread simulatorThread = new Thread(() -> {
            logger.info("Starting temperature simulator for sensor: {} ({})", sensorId, sensorName);

            // Get or set base temperature for this sensor
            double baseTemp = baseTemperatures.computeIfAbsent(sensorId, k -> DEFAULT_BASE_TEMPERATURE);
            long pushIntervalMs = 10000; // Push every 2 seconds

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    // Generate temperature with random variance
                    double temperature = generateTemperature(baseTemp);

                    // Create TemperatureData
                    TemperatureData data = TemperatureData.newBuilder()
                            .setSensorId(sensorId)
                            .setSensorName(sensorName)
                            .setForestId(forestId)
                            .setTemperature(temperature)
                            .setTimestamp(System.currentTimeMillis())
                            .build();

                    // Check and trigger alerts if necessary
                    alertService.checkAndTriggerAlert(sensorId, sensorName, forestId, temperature);

                    // Notify all subscribers
                    CopyOnWriteArrayList<Consumer<TemperatureData>> subs = subscribers.get(sensorId);
                    if (subs != null && !subs.isEmpty()) {
                        subs.forEach(callback -> {
                            try {
                                callback.accept(data);
                            } catch (Exception e) {
                                logger.error("Error notifying subscriber for sensor: {}", sensorId, e);
                            }
                        });
                    }

                    // Sleep before next push
                    Thread.sleep(pushIntervalMs);

                } catch (InterruptedException e) {
                    logger.info("Temperature simulator interrupted for sensor: {}", sensorId);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in temperature simulator for sensor: {}", sensorId, e);
                }
            }

            logger.info("Temperature simulator stopped for sensor: {}", sensorId);
            simulatorThreads.remove(sensorId);
        }, "TemperatureSimulator-" + sensorId);

        simulatorThread.setDaemon(true);
        simulatorThread.start();
        simulatorThreads.put(sensorId, simulatorThread);
    }

    /**
     * Stop simulator thread for a sensor
     */
    private void stopSimulator(String sensorId) {
        Thread thread = simulatorThreads.get(sensorId);
        if (thread != null) {
            logger.info("Stopping temperature simulator for sensor: {}", sensorId);
            thread.interrupt();
            simulatorThreads.remove(sensorId);
        }
    }

    /**
     * Generate temperature with random variance
     */
    private double generateTemperature(double baseTemp) {
        double variance = (random.nextDouble() - 0.5) * 2 * TEMPERATURE_VARIANCE;
        double temperature = baseTemp + variance;
        // Round to 1 decimal place
        return Math.round(temperature * 10.0) / 10.0;
    }
}

