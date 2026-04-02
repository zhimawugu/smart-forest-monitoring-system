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
        // Generate a unique client ID for internal management
        String clientId = UUID.randomUUID().toString();

        // Register this client's observer
        alertService.watchAlerts(clientId, responseObserver);

        // Return a StreamObserver to handle incoming messages from client
        return new StreamObserver<AlertMessage>() {
            @Override
            public void onNext(AlertMessage message) {
                try {
                    // Process SetAlertRequest
                    if (message.hasSetAlert()) {
                        alertService.setAlertThreshold(message.getSetAlert());
                    }
                } catch (Exception e) {
                    logger.error("Error processing alert message: {}", e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Alert watch stream error: {}", t.getMessage());
                alertService.unregisterClient(clientId);
            }

            @Override
            public void onCompleted() {
                alertService.unregisterClient(clientId);
                responseObserver.onCompleted();
            }
        };
    }
}
