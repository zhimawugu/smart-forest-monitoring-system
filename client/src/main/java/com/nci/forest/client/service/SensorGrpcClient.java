package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sensor gRPC Client Service
 */
public class SensorGrpcClient {

    private static final Logger logger = LoggerFactory.getLogger(SensorGrpcClient.class);
    private static final long DEFAULT_DEADLINE_SECONDS = 30;

    private final ManagedChannel channel;
    private final SensorServiceGrpc.SensorServiceBlockingStub blockingStub;
    private long deadlineSeconds = DEFAULT_DEADLINE_SECONDS;

    public SensorGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = SensorServiceGrpc.newBlockingStub(channel);
        logger.info("Sensor gRPC client connected to {}:{}", host, port);
    }

    public SensorGrpcClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve();
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.blockingStub = SensorServiceGrpc.newBlockingStub(channel);
        logger.info("Sensor gRPC client connected to {}:{}", endpoint.host(), endpoint.port());
    }

    /**
     * Add sensor to forest
     */
    public AddSensorResponse addSensor(String forestId, String sensorName,
                                       double latitude, double longitude) {
        try {
            logger.info("Adding sensor: {} to forest: {}", sensorName, forestId);

            AddSensorRequest request = AddSensorRequest.newBuilder()
                    .setForestId(forestId)
                    .setName(sensorName)
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .build();

            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            AddSensorResponse response = stubWithDeadline.addSensor(request);
            logger.info("Sensor added successfully: {}", response.getSensor().getId());
            return response;
        } catch (Exception e) {
            logger.error("Error adding sensor", e);
            throw new RuntimeException("Failed to add sensor: " + e.getMessage(), e);
        }
    }

    /**
     * Remove sensor from forest
     */
    public RemoveSensorResponse removeSensor(String sensorId) {
        try {
            logger.info("Removing sensor: {}", sensorId);

            RemoveSensorRequest request = RemoveSensorRequest.newBuilder()
                    .setSensorId(sensorId)
                    .build();

            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            RemoveSensorResponse response = stubWithDeadline.removeSensor(request);
            logger.info("Sensor removed: {}", response.getMessage());
            return response;
        } catch (Exception e) {
            logger.error("Error removing sensor", e);
            throw new RuntimeException("Failed to remove sensor: " + e.getMessage(), e);
        }
    }

    /**
     * List all sensors in a forest
     */
    public List<Sensor> listSensors(String forestId) {
        try {
            logger.info("Listing sensors for forest: {}", forestId);

            ListSensorsRequest request = ListSensorsRequest.newBuilder()
                    .setForestId(forestId)
                    .build();

            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            ListSensorsResponse response = stubWithDeadline.listSensors(request);
            logger.info("Found {} sensors in forest: {}", response.getTotal(), forestId);
            return new ArrayList<>(response.getSensorsList());
        } catch (Exception e) {
            logger.error("Error listing sensors", e);
            throw new RuntimeException("Failed to list sensors: " + e.getMessage(), e);
        }
    }

    /**
     * Set the deadline for gRPC calls (in seconds)
     */
    public void setDeadlineSeconds(long seconds) {
        this.deadlineSeconds = seconds;
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown();
        logger.info("Sensor gRPC client shutdown");
    }
}

