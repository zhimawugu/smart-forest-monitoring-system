package com.nci.forest.server.grpc;

import com.nci.forest.proto.*;
import com.nci.forest.server.service.AlertService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * gRPC Service Implementation for Alert Service
 * Handles bidirectional streaming of alert messages
 */
@GrpcService
public class AlertServiceImpl extends AlertServiceGrpc.AlertServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(AlertServiceImpl.class);

    @Autowired
    private AlertService alertService;

    @Override
    public StreamObserver<AlertMessage> watchAlerts(StreamObserver<AlertEvent> responseObserver) {
        // Generate a unique client ID
        String clientId = UUID.randomUUID().toString();
        logger.info("gRPC: Client {} connected to watch alerts", clientId);

        // Register this client's observer
        alertService.watchAlerts(clientId, responseObserver);

        // Return a StreamObserver to handle incoming messages from client
        return new StreamObserver<>() {
            @Override
            public void onNext(AlertMessage message) {
                try {
                    // Process SetAlertRequest
                    if (message.hasSetAlert()) {
                        SetAlertRequest request = message.getSetAlert();
                        logger.info("gRPC: Received SetAlertRequest for sensor: {}", request.getSensorId());
                        alertService.setAlertThreshold(request);
                    }
                } catch (Exception e) {
                    logger.error("Error processing alert message from client {}: {}", clientId, e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in watch alerts stream for client {}: {}", clientId, t.getMessage());
                alertService.unregisterClient(clientId);
            }

            @Override
            public void onCompleted() {
                logger.info("Client {} closed alert watch stream", clientId);
                alertService.unregisterClient(clientId);
                responseObserver.onCompleted();
            }
        };
    }
}

