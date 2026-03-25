package com.nci.forest.server.grpc;

import com.nci.forest.proto.*;
import com.nci.forest.server.service.SensorService;
import com.nci.forest.server.service.TemperatureMonitoringService;
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
    private TemperatureMonitoringService temperatureMonitoringService;

    @Override
    public void addSensor(AddSensorRequest request, StreamObserver<AddSensorResponse> responseObserver) {
        try {
            logger.info("gRPC: Adding sensor - {}", request.getName());

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
    public StreamObserver<TemperatureData> streamTemperatureData(
            StreamObserver<com.nci.forest.proto.Empty> responseObserver) {
        logger.info("gRPC: Client connected for streaming temperature data");

        return new StreamObserver<TemperatureData>() {
            @Override
            public void onNext(TemperatureData temperatureData) {
                try {
                    logger.debug("Received temperature data from client: sensor={}, temp={}",
                               temperatureData.getSensorId(), temperatureData.getTemperature());

                    // Record the temperature data and trigger alerts
                    temperatureMonitoringService.recordTemperatureData(temperatureData);

                } catch (Exception e) {
                    logger.error("Error processing temperature data: {}", e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in temperature streaming: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                logger.info("Temperature streaming completed");
                responseObserver.onCompleted();
            }
        };
    }
}

