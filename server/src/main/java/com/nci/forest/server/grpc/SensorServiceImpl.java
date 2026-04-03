package com.nci.forest.server.grpc;

import com.nci.forest.proto.*;
import com.nci.forest.server.service.SensorService;
import com.nci.forest.server.util.LocationValidator;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * gRPC Service Implementation for Sensor Service
 */
@GrpcService
public class SensorServiceImpl extends SensorServiceGrpc.SensorServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(SensorServiceImpl.class);

    @Autowired
    private SensorService sensorService;

    @Autowired
    private com.nci.forest.server.service.TemperatureSimulatorService temperatureSimulatorService;

    @Override
    public void addSensor(AddSensorRequest request, StreamObserver<AddSensorResponse> responseObserver) {
        try {
            // Validate location coordinates
            String locationError = LocationValidator.validateCoordinates(request.getLatitude(), request.getLongitude());
            if (locationError != null) {
                responseObserver.onError(new IllegalArgumentException(locationError));
                return;
            }

            // Call service to add sensor
            AddSensorResponse response = sensorService.addSensor(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error adding sensor", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void removeSensor(RemoveSensorRequest request, StreamObserver<RemoveSensorResponse> responseObserver) {
        try {
            RemoveSensorResponse response = sensorService.removeSensor(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error removing sensor", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void listSensors(ListSensorsRequest request, StreamObserver<ListSensorsResponse> responseObserver) {
        try {
            ListSensorsResponse response = sensorService.listSensors(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error listing sensors", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void streamTemperatureData(StreamTemperatureRequest request, StreamObserver<TemperatureData> responseObserver) {
        String sensorId = request.getSensorId();
        String forestId = request.getForestId();

        try {
            // Set up cancellation handler
            Context.current().addListener(context -> {
                temperatureSimulatorService.unsubscribe(sensorId, null);
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

            // Get sensor details
            ListSensorsRequest listRequest = ListSensorsRequest.newBuilder().setForestId(forestId).build();
            ListSensorsResponse sensors = sensorService.listSensors(listRequest);

            // Find the sensor with matching ID
            String sensorName = null;
            for (Sensor sensor : sensors.getSensorsList()) {
                if (sensor.getId().equals(sensorId)) {
                    sensorName = sensor.getName();
                    break;
                }
            }

            if (sensorName == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Sensor not found").asException());
                return;
            }

            // Create a callback for temperature data
            temperatureSimulatorService.subscribe(sensorId, sensorName, forestId, temperatureData -> {
                try {
                    // Check deadline
                    if (Context.current().getDeadline() != null) {
                        long timeRemainingMs = Context.current().getDeadline().timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (timeRemainingMs <= 0) {
                            responseObserver.onError(Status.DEADLINE_EXCEEDED.withDescription("Stream deadline exceeded").asException());
                            return;
                        }
                    }
                    responseObserver.onNext(temperatureData);
                } catch (Exception e) {
                    logger.error("Error sending temperature data: {}", e.getMessage());
                }
            });

            // Unsubscribe when stream ends
            Context.current().addListener(context -> {
                temperatureSimulatorService.unsubscribe(sensorId, null);
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            logger.error("Error in temperature stream for sensor: {}", sensorId, e);
            responseObserver.onError(e);
        }
    }
}
