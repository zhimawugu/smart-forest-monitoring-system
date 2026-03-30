package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Alert gRPC Client Service
 * Manages bidirectional communication with server for alert monitoring
 */
public class AlertGrpcClient {

    private static final Logger logger = LoggerFactory.getLogger(AlertGrpcClient.class);
    private static final long DEFAULT_DEADLINE_SECONDS = 30;
    private static final String SERVICE_TYPE = "_forest-grpc._tcp.local.";

    private final ManagedChannel channel;
    private final AlertServiceGrpc.AlertServiceStub asyncStub;
    private StreamObserver<AlertMessage> requestObserver;
    private long deadlineSeconds = DEFAULT_DEADLINE_SECONDS;

    public interface AlertEventCallback {
        void onAlertReceived(AlertEvent event);

        void onError(Throwable t);

        void onCompleted();
    }

    public AlertGrpcClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve(SERVICE_TYPE);
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.asyncStub = AlertServiceGrpc.newStub(channel);
        logger.info("Alert gRPC client created for {}:{}", endpoint.host(), endpoint.port());
    }

    /**
     * Start watching alerts (bidirectional streaming)
     */
    public void startWatchingAlerts(AlertEventCallback callback) {
        logger.info("Starting to watch alerts from server");

        // Create response observer for receiving alerts from server
        StreamObserver<AlertEvent> responseObserver = new StreamObserver<AlertEvent>() {
            @Override
            public void onNext(AlertEvent alertEvent) {
                logger.warn("Alert received: sensor={}, type={}, temp={}°C",
                           alertEvent.getSensorId(), alertEvent.getAlertType(),
                           alertEvent.getCurrentTemperature());
                if (callback != null) {
                    callback.onAlertReceived(alertEvent);
                }
            }

            @Override
            public void onError(Throwable t) {
                // Check if error was due to deadline exceeded
                if (t instanceof io.grpc.StatusRuntimeException) {
                    io.grpc.StatusRuntimeException e = (io.grpc.StatusRuntimeException) t;
                    if (e.getStatus() == io.grpc.Status.DEADLINE_EXCEEDED) {
                        logger.error("Alert watch stream exceeded deadline of {} seconds", deadlineSeconds);
                    } else if (e.getStatus() == io.grpc.Status.CANCELLED) {
                        logger.info("Alert watch stream was cancelled");
                    }
                }
                logger.error("Alert watch stream error: {}", t.getMessage());
                if (callback != null) {
                    callback.onError(t);
                }
            }

            @Override
            public void onCompleted() {
                logger.info("Alert watch stream completed");
                if (callback != null) {
                    callback.onCompleted();
                }
            }
        };

        try {
            // Create bidirectional stream with deadline and store request observer
            AlertServiceGrpc.AlertServiceStub asyncStubWithDeadline = 
                asyncStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            this.requestObserver = asyncStubWithDeadline.watchAlerts(responseObserver);
            logger.info("Alert watch stream established with {} second deadline", deadlineSeconds);
        } catch (io.grpc.StatusRuntimeException e) {
            logger.error("Failed to establish alert watch stream: {}", e.getMessage());
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    /**
     * Set alert threshold for a sensor (only max temperature)
     */
    public void setAlertThreshold(String sensorId, double maxTemp) {
        if (requestObserver == null) {
            logger.error("Alert watch stream not established. Call startWatchingAlerts first");
            return;
        }

        try {
            SetAlertRequest setAlertRequest = SetAlertRequest.newBuilder()
                    .setSensorId(sensorId)
                    .setMaxTemperature(maxTemp)
                    .build();

            AlertMessage message = AlertMessage.newBuilder()
                    .setSetAlert(setAlertRequest)
                    .build();

            requestObserver.onNext(message);
            logger.info("Alert threshold set for sensor {}: max={}", sensorId, maxTemp);
        } catch (Exception e) {
            logger.error("Error setting alert threshold for sensor {}: {}", sensorId, e.getMessage());
        }
    }

    /**
     * Set the deadline for gRPC calls (in seconds)
     */
    public void setDeadlineSeconds(long seconds) {
        this.deadlineSeconds = seconds;
    }

    /**
     * Close the alert watch stream and channel
     */
    public void close() {
        try {
            if (requestObserver != null) {
                requestObserver.onCompleted();
            }
            channel.shutdown();
            logger.info("Alert gRPC client shutdown");
        } catch (Exception e) {
            logger.error("Error closing alert gRPC client: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return requestObserver != null && !channel.isShutdown();
    }
}
