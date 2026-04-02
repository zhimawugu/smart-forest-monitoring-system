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
 * Simulates real-time temperature data streaming for sensors
 */
@Service
public class TemperatureSimulatorService {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureSimulatorService.class);

    private static final Random random = new Random();
    private static final double TEMPERATURE_VARIANCE = 5.0;
    private static final double DEFAULT_BASE_TEMPERATURE = 25.0;
    private static final double HIGH_TEMP_CHANCE = 0.2;
    private static final double HIGH_TEMP_BOOST = 10.0;

    private final ConcurrentHashMap<String, Double> baseTemperatures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<TemperatureData>>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> simulatorThreads = new ConcurrentHashMap<>();
    private volatile boolean isRunning = true;

    @Autowired
    private AlertService alertService;

    /**
     * Subscribe to temperature stream for a specific sensor
     */
    public void subscribe(String sensorId, String sensorName, String forestId, Consumer<TemperatureData> callback) {
        subscribers.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(callback);
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
            double baseTemp = baseTemperatures.computeIfAbsent(sensorId, k -> DEFAULT_BASE_TEMPERATURE);
            long pushIntervalMs = 10000;

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    double temperature = generateTemperature(baseTemp);

                    TemperatureData data = TemperatureData.newBuilder()
                            .setSensorId(sensorId)
                            .setSensorName(sensorName)
                            .setForestId(forestId)
                            .setTemperature(temperature)
                            .setTimestamp(System.currentTimeMillis())
                            .build();

                    alertService.checkAndTriggerAlert(sensorId, sensorName, forestId, temperature);

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

                    Thread.sleep(pushIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in temperature simulator for sensor: {}", sensorId, e);
                }
            }
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
            thread.interrupt();
            simulatorThreads.remove(sensorId);
        }
    }

    /**
     * Generate temperature with random variance
     * Occasionally generates high temperatures to trigger alerts
     */
    private double generateTemperature(double baseTemp) {
        double variance = (random.nextDouble() - 0.5) * 2 * TEMPERATURE_VARIANCE;
        double temperature = baseTemp + variance;
        if (random.nextDouble() < HIGH_TEMP_CHANCE) {
            temperature += HIGH_TEMP_BOOST;
        }
        return Math.round(temperature * 10.0) / 10.0;
    }
}
