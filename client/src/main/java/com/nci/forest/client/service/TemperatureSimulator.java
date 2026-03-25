package com.nci.forest.client.service;

import com.nci.forest.proto.TemperatureData;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sensor Temperature Data Simulator
 * Simulates temperature readings and streams them to server
 */
public class TemperatureSimulator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureSimulator.class);

    private final String sensorId;
    private final String sensorName;
    private final String forestId;
    private final TemperatureDataCallback callback;
    private final StreamObserver<TemperatureData> serverObserver;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();
    private double baseTemperature = 20.0;

    public interface TemperatureDataCallback {
        void onTemperatureData(TemperatureData data);
    }

    public TemperatureSimulator(String sensorId, String sensorName, String forestId,
                               TemperatureDataCallback callback) {
        this(sensorId, sensorName, forestId, callback, null);
    }

    public TemperatureSimulator(String sensorId, String sensorName, String forestId,
                               TemperatureDataCallback callback,
                               StreamObserver<TemperatureData> serverObserver) {
        this.sensorId = sensorId;
        this.sensorName = sensorName;
        this.forestId = forestId;
        this.callback = callback;
        this.serverObserver = serverObserver;
    }

    public void start() {
        running.set(true);
        new Thread(this).start();
        logger.info("Temperature simulator started for sensor: {}", sensorId);
    }

    public void stop() {
        running.set(false);
        if (serverObserver != null) {
            try {
                serverObserver.onCompleted();
            } catch (Exception e) {
                logger.error("Error closing server observer: {}", e.getMessage());
            }
        }
        logger.info("Temperature simulator stopped for sensor: {}", sensorId);
    }

    @Override
    public void run() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        while (running.get()) {
            try {
                // Simulate temperature variation (±2°C from base)
                double variation = (random.nextDouble() - 0.5) * 4.0;
                double currentTemperature = baseTemperature + variation;

                // Slightly change base temperature over time (±0.1°C)
                baseTemperature += (random.nextDouble() - 0.5) * 0.2;
                baseTemperature = Math.max(15.0, Math.min(35.0, baseTemperature));

                // Create temperature data
                TemperatureData data = TemperatureData.newBuilder()
                        .setSensorId(sensorId)
                        .setSensorName(sensorName)
                        .setForestId(forestId)
                        .setTemperature(Math.round(currentTemperature * 100.0) / 100.0)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                // Send to callback (UI)
                if (callback != null) {
                    callback.onTemperatureData(data);
                }

                // Send to server for alert monitoring
                if (serverObserver != null) {
                    try {
                        serverObserver.onNext(data);
                    } catch (Exception e) {
                        logger.error("Error sending temperature to server: {}", e.getMessage());
                    }
                }

                logger.debug("Sensor {} temperature: {}", sensorId, data.getTemperature());

                // Sleep for 2 seconds before next reading
                Thread.sleep(2000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in temperature simulator for sensor: {}", sensorId, e);
            }
        }
    }
}

