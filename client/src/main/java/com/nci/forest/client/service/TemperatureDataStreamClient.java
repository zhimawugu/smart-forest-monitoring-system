package com.nci.forest.client.service;

import com.nci.forest.proto.SensorServiceGrpc;
import com.nci.forest.proto.StreamTemperatureRequest;
import com.nci.forest.proto.TemperatureData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Temperature Data Stream Client
 * Receives real-time temperature data from server
 */
public class TemperatureDataStreamClient {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureDataStreamClient.class);
    private static final String SERVICE_TYPE = "_forest-grpc._tcp.local.";

    private final ManagedChannel channel;
    private final SensorServiceGrpc.SensorServiceStub asyncStub;

    public TemperatureDataStreamClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = SensorServiceGrpc.newStub(channel);
        logger.info("Temperature stream client created for {}:{}", host, port);
    }

    public TemperatureDataStreamClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve(SERVICE_TYPE);
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.asyncStub = SensorServiceGrpc.newStub(channel);
        logger.info("Temperature stream client created for {}:{}", endpoint.host(), endpoint.port());
    }

    /**
     * Start streaming temperature data from server for a specific sensor
     * @param sensorId the sensor ID to stream data from
     * @param forestId the forest ID containing the sensor
     * @param dataCallback callback invoked for each temperature data received
     */
    public void startStreamingTemperatureData(String sensorId, String forestId, Consumer<TemperatureData> dataCallback) {
        logger.info("Starting to receive temperature data from server for sensor: {}", sensorId);

        // Create the request
        StreamTemperatureRequest request = StreamTemperatureRequest.newBuilder()
                .setSensorId(sensorId)
                .setForestId(forestId)
                .build();

        // Create response observer to handle incoming data
        StreamObserver<TemperatureData> responseObserver = new StreamObserver<TemperatureData>() {
            @Override
            public void onNext(TemperatureData temperatureData) {
                logger.debug("Received temperature data: sensor={}, temp={}°C",
                           temperatureData.getSensorId(), temperatureData.getTemperature());
                // Pass data to callback
                dataCallback.accept(temperatureData);
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in temperature stream: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                logger.info("Temperature stream completed for sensor: {}", sensorId);
            }
        };

        // Start the server-side streaming
        asyncStub.streamTemperatureData(request, responseObserver);
    }

    /**
     * Close the client connection
     */
    public void close() {
        try {
            channel.shutdown();
            logger.info("Temperature stream client shutdown");
        } catch (Exception e) {
            logger.error("Error closing temperature stream client: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return !channel.isShutdown();
    }
}
