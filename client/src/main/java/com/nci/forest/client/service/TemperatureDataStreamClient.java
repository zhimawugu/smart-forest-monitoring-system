package com.nci.forest.client.service;

import com.nci.forest.proto.Empty;
import com.nci.forest.proto.SensorServiceGrpc;
import com.nci.forest.proto.TemperatureData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temperature Data Stream Client
 * Sends temperature data to server for monitoring and alert processing
 */
public class TemperatureDataStreamClient {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureDataStreamClient.class);

    private final ManagedChannel channel;
    private final SensorServiceGrpc.SensorServiceStub asyncStub;
    private StreamObserver<TemperatureData> requestObserver;

    public TemperatureDataStreamClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = SensorServiceGrpc.newStub(channel);
        logger.info("Temperature stream client created for {}:{}", host, port);
    }

    public TemperatureDataStreamClient() {
        this("localhost", 50051);
    }

    /**
     * Start the temperature data streaming
     */
    public void startStreaming() {
        logger.info("Starting temperature data streaming to server");

        // Create response observer
        StreamObserver<Empty> responseObserver = new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
                // Server sends empty response
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Temperature stream error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                logger.info("Temperature stream completed");
            }
        };

        // Start the bidirectional stream
        this.requestObserver = asyncStub.streamTemperatureData(responseObserver);
    }

    /**
     * Send temperature data to server
     */
    public void sendTemperatureData(TemperatureData temperatureData) {
        if (requestObserver == null) {
            logger.warn("Stream not initialized, initializing now");
            startStreaming();
        }

        try {
            requestObserver.onNext(temperatureData);
            logger.debug("Temperature data sent: sensor={}, temp={}°C",
                        temperatureData.getSensorId(), temperatureData.getTemperature());
        } catch (Exception e) {
            logger.error("Error sending temperature data: {}", e.getMessage());
            // Try to reinitialize stream
            try {
                startStreaming();
                requestObserver.onNext(temperatureData);
            } catch (Exception e2) {
                logger.error("Failed to resend temperature data: {}", e2.getMessage());
            }
        }
    }

    /**
     * Close the stream
     */
    public void close() {
        try {
            if (requestObserver != null) {
                requestObserver.onCompleted();
            }
            channel.shutdown();
            logger.info("Temperature stream client shutdown");
        } catch (Exception e) {
            logger.error("Error closing temperature stream client: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return requestObserver != null && !channel.isShutdown();
    }
}

