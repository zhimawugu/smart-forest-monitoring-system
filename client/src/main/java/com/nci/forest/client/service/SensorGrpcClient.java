package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
    private static final String SERVICE_TYPE = "_forest-grpc._tcp.local.";

    private final ManagedChannel channel;
    private final SensorServiceGrpc.SensorServiceBlockingStub blockingStub;
    private final long deadlineSeconds = DEFAULT_DEADLINE_SECONDS;

    public SensorGrpcClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve(SERVICE_TYPE);
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.blockingStub = SensorServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Add sensor to forest
     */
    public AddSensorResponse addSensor(String forestId, String sensorName,
                                       double latitude, double longitude) {
        try {
            AddSensorRequest request = AddSensorRequest.newBuilder()
                    .setForestId(forestId)
                    .setName(sensorName)
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .build();

            // Set deadline for the request - automatically times out after specified seconds
            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            AddSensorResponse response = stubWithDeadline.addSensor(request);
            return response;
        } catch (io.grpc.StatusRuntimeException e) {
            // Check if error was due to deadline exceeded
            if (e.getStatus() == io.grpc.Status.DEADLINE_EXCEEDED) {
                throw new RuntimeException("Request timeout: exceeded " + deadlineSeconds + " seconds", e);
            }
            // Check if error was due to cancellation
            if (e.getStatus() == io.grpc.Status.CANCELLED) {
                throw new RuntimeException("Request was cancelled", e);
            }
            logger.error("Error adding sensor", e);
            throw new RuntimeException("Failed to add sensor: " + e.getMessage(), e);
        }
    }

    /**
     * Remove sensor from forest
     */
    public RemoveSensorResponse removeSensor(String sensorId) {
        try {
            RemoveSensorRequest request = RemoveSensorRequest.newBuilder()
                    .setSensorId(sensorId)
                    .build();

            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            RemoveSensorResponse response = stubWithDeadline.removeSensor(request);
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
            ListSensorsRequest request = ListSensorsRequest.newBuilder()
                    .setForestId(forestId)
                    .build();

            SensorServiceGrpc.SensorServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            ListSensorsResponse response = stubWithDeadline.listSensors(request);
            return new ArrayList<>(response.getSensorsList());
        } catch (Exception e) {
            logger.error("Error listing sensors", e);
            throw new RuntimeException("Failed to list sensors: " + e.getMessage(), e);
        }
    }
}
