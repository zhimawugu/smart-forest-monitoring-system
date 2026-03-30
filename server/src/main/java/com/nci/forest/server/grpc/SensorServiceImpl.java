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
        logger.info("gRPC: Adding sensor - {}", request.getName());

        try {

            // Set up cancellation handler to detect when client cancels
            Context.current().addListener(context -> {
                logger.warn("CANCELLATION DETECTED for addSensor request");
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

            // Check deadline - return error if already exceeded
            if (Context.current().getDeadline() != null) {
                long timeRemainingMs = Context.current().getDeadline().timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS);
                if (timeRemainingMs <= 0) {
                    logger.warn("Deadline exceeded before processing");
                    responseObserver.onError(
                        Status.DEADLINE_EXCEEDED.withDescription("Request deadline exceeded").asException()
                    );
                    return;
                }
                logger.info("Sensor request has {} ms remaining", timeRemainingMs);
            }

            // Validate location coordinates
            String locationError = LocationValidator.validateCoordinates(request.getLatitude(), request.getLongitude());
            if (locationError != null) {
                logger.warn("AddSensor validation failed: {}", locationError);
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
            logger.info("gRPC: Removing sensor - {}", request.getSensorId());

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
            logger.info("gRPC: Listing sensors for forest - {}", request.getForestId());

            ListSensorsResponse response = sensorService.listSensors(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error listing sensors", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void streamTemperatureData(StreamTemperatureRequest request, 
                                     StreamObserver<TemperatureData> responseObserver) {
        String sensorId = request.getSensorId();
        String forestId = request.getForestId();
        
        logger.info("gRPC: Client requesting temperature stream for sensor: {}", sensorId);

        try {

            // Set up cancellation handler to detect when client cancels the stream
            Context.current().addListener(context -> {
                logger.warn("CANCELLATION DETECTED for temperature stream");
                temperatureSimulatorService.unsubscribe(sensorId, null);
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

            // Get sensor details
            ListSensorsRequest listRequest = ListSensorsRequest.newBuilder()
                    .setForestId(forestId)
                    .build();
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
                logger.warn("Sensor not found: {}", sensorId);
                responseObserver.onError(
                    Status.NOT_FOUND.withDescription("Sensor not found").asException()
                );
                return;
            }

            // Create a callback that will be invoked for each temperature data point
            temperatureSimulatorService.subscribe(sensorId, sensorName, forestId, temperatureData -> {
                try {

                    // Check deadline
                    if (Context.current().getDeadline() != null) {
                        long timeRemainingMs = Context.current().getDeadline()
                                .timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (timeRemainingMs <= 0) {
                            logger.warn("Deadline exceeded for sensor stream: {}", sensorId);
                            responseObserver.onError(
                                Status.DEADLINE_EXCEEDED.withDescription("Stream deadline exceeded").asException()
                            );
                            return;
                        }
                    }

                    // Send temperature data to client
                    responseObserver.onNext(temperatureData);
                    logger.debug("Sent temperature data to client: sensor={}, temp={}°C",
                               sensorId, temperatureData.getTemperature());

                } catch (Exception e) {
                    logger.error("Error sending temperature data: {}", e.getMessage());
                }
            });

            // Register a listener to unsubscribe when stream completes or errors
            Context.current().addListener(context -> {
                logger.info("Temperature stream context ended for sensor: {}", sensorId);
                temperatureSimulatorService.unsubscribe(sensorId, null);
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            logger.error("Error in temperature stream for sensor: {}", sensorId, e);
            responseObserver.onError(e);
        }
    }
}
