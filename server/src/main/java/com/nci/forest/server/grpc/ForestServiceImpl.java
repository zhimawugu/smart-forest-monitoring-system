package com.nci.forest.server.grpc;

import com.nci.forest.proto.*;
import com.nci.forest.server.util.LocationValidator;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Forest Service
 * Provides CRUD operations for forest management
 */
@GrpcService
public class ForestServiceImpl extends ForestServiceGrpc.ForestServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(ForestServiceImpl.class);

    // In-memory storage for forests (using ConcurrentHashMap for thread safety)
    private final Map<String, Forest> forestStore = new ConcurrentHashMap<>();

    @Override
    public void addForest(AddForestRequest request, StreamObserver<AddForestResponse> responseObserver) {
        try {
            // Check deadline - return error if already exceeded
            if (Context.current().getDeadline() != null) {
                long timeRemainingMs = Context.current().getDeadline().timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS);
                if (timeRemainingMs <= 0) {
                    responseObserver.onError(Status.DEADLINE_EXCEEDED.withDescription("Request deadline exceeded").asException());
                    return;
                }
            }

            // Validate request - name
            if (request.getName() == null || request.getName().isEmpty()) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Validation failed: Forest name cannot be empty").asException());
                return;
            }

            // Validate location coordinates
            String locationError = LocationValidator.validateCoordinates(request.getLatitude(), request.getLongitude());
            if (locationError != null) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Validation failed: " + locationError).asException());
                return;
            }

            // Generate unique ID for the forest
            String forestId = UUID.randomUUID().toString();

            // Create forest object
            Forest forest = Forest.newBuilder().setId(forestId).setName(request.getName()).setLatitude(request.getLatitude()).setLongitude(request.getLongitude()).build();

            // Store the forest
            forestStore.put(forestId, forest);

            // Send success response
            sendAddForestResponse(true, "Forest added successfully", forest, responseObserver);

        } catch (Exception e) {
            logger.error("Error adding forest: {}", e.getMessage(), e);
            sendAddForestResponse(false, "Internal error: " + e.getMessage(), null, responseObserver);
        }
    }

    @Override
    public StreamObserver<DeleteForestRequest> deleteForest(StreamObserver<DeleteForestResponse> responseObserver) {
        return new StreamObserver<>() {
            private final StringBuilder errorMessages = new StringBuilder();
            private int successCount = 0;
            private int failureCount = 0;

            @Override
            public void onNext(DeleteForestRequest request) {
                try {
                    // Validate request
                    if (request.getId() == null || request.getId().isEmpty()) {
                        failureCount++;
                        errorMessages.append("Forest ID cannot be empty; ");
                        return;
                    }

                    // Check if forest exists
                    if (!forestStore.containsKey(request.getId())) {
                        failureCount++;
                        errorMessages.append("Forest not found: ").append(request.getId()).append("; ");
                        return;
                    }

                    // Delete the forest
                    forestStore.remove(request.getId());
                    successCount++;

                } catch (Exception e) {
                    failureCount++;
                    errorMessages.append("Error: ").append(e.getMessage()).append("; ");
                    logger.error("Error deleting forest: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in DeleteForest stream: {}", t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                // Build response message
                String message;
                boolean success = successCount > 0;

                if (failureCount == 0) {
                    message = String.format("Successfully deleted %d forest(s)", successCount);
                } else if (successCount == 0) {
                    message = String.format("Failed to delete forests: %s", errorMessages);
                } else {
                    message = String.format("Deleted %d forest(s), %d failed: %s", successCount, failureCount, errorMessages);
                }

                // Send response
                DeleteForestResponse response = DeleteForestResponse.newBuilder().setSuccess(success).setMessage(message).build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void listForests(ListForestsRequest request, StreamObserver<ListForestsResponse> responseObserver) {
        try {
            // to test request deadline
//            Thread.sleep(6000);
            // Get all forests from the store
            List<Forest> forests = new ArrayList<>(forestStore.values());

            // Build and send response
            ListForestsResponse response = ListForestsResponse.newBuilder().addAllForests(forests).setTotal(forests.size()).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error listing forests: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Helper method to send AddForestResponse
     */
    private void sendAddForestResponse(boolean success, String message, Forest forest, StreamObserver<AddForestResponse> responseObserver) {
        AddForestResponse.Builder responseBuilder = AddForestResponse.newBuilder().setSuccess(success).setMessage(message);

        if (forest != null) {
            responseBuilder.setForest(forest);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
