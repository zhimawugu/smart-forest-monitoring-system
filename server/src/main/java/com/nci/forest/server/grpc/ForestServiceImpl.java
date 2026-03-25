package com.nci.forest.server.grpc;

import com.nci.forest.proto.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
        logger.info("Received AddForest request: name={}", request.getName());

        try {
            // Validate request
            if (request.getName() == null || request.getName().isEmpty()) {
                sendAddForestResponse(false, "Forest name cannot be empty", null, responseObserver);
                return;
            }

            if (request.getLocation() == null) {
                sendAddForestResponse(false, "Location cannot be null", null, responseObserver);
                return;
            }

            // Generate unique ID for the forest
            String forestId = UUID.randomUUID().toString();

            // Create forest object
            Forest forest = Forest.newBuilder()
                    .setId(forestId)
                    .setName(request.getName())
                    .setLocation(request.getLocation())
                    .build();

            // Store the forest
            forestStore.put(forestId, forest);

            logger.info("Forest added successfully: id={}, name={}", forestId, request.getName());

            // Send success response
            sendAddForestResponse(true, "Forest added successfully", forest, responseObserver);

        } catch (Exception e) {
            logger.error("Error adding forest: {}", e.getMessage(), e);
            sendAddForestResponse(false, "Internal error: " + e.getMessage(), null, responseObserver);
        }
    }

    @Override
    public StreamObserver<DeleteForestRequest> deleteForest(StreamObserver<DeleteForestResponse> responseObserver) {
        logger.info("Client connected for streaming DeleteForest requests");

        return new StreamObserver<DeleteForestRequest>() {
            private int successCount = 0;
            private int failureCount = 0;
            private final StringBuilder errorMessages = new StringBuilder();

            @Override
            public void onNext(DeleteForestRequest request) {
                logger.info("Received DeleteForest request: id={}", request.getId());

                try {
                    // Validate request
                    if (request.getId() == null || request.getId().isEmpty()) {
                        failureCount++;
                        errorMessages.append("Forest ID cannot be empty; ");
                        logger.warn("Invalid delete request: empty ID");
                        return;
                    }

                    // Check if forest exists
                    if (!forestStore.containsKey(request.getId())) {
                        failureCount++;
                        errorMessages.append("Forest not found: ").append(request.getId()).append("; ");
                        logger.warn("Forest not found: {}", request.getId());
                        return;
                    }

                    // Delete the forest
                    Forest removedForest = forestStore.remove(request.getId());
                    successCount++;

                    logger.info("Forest deleted successfully: id={}, name={}", request.getId(), removedForest.getName());

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
                logger.info("DeleteForest stream completed. Success: {}, Failure: {}", successCount, failureCount);

                // Build response message
                String message;
                boolean success = successCount > 0;

                if (failureCount == 0) {
                    message = String.format("Successfully deleted %d forest(s)", successCount);
                } else if (successCount == 0) {
                    message = String.format("Failed to delete forests: %s", errorMessages.toString());
                } else {
                    message = String.format("Deleted %d forest(s), %d failed: %s",
                            successCount, failureCount, errorMessages.toString());
                }

                // Send response
                DeleteForestResponse response = DeleteForestResponse.newBuilder()
                        .setSuccess(success)
                        .setMessage(message)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void listForests(ListForestsRequest request, StreamObserver<ListForestsResponse> responseObserver) {
        logger.info("Received ListForests request");

        try {
            // Get all forests from the store
            List<Forest> forests = new ArrayList<>(forestStore.values());

            logger.info("Returning {} forests", forests.size());

            // Build and send response
            ListForestsResponse response = ListForestsResponse.newBuilder()
                    .addAllForests(forests)
                    .setTotal(forests.size())
                    .build();

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
    private void sendAddForestResponse(boolean success, String message, Forest forest,
                                       StreamObserver<AddForestResponse> responseObserver) {
        AddForestResponse.Builder responseBuilder = AddForestResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message);

        if (forest != null) {
            responseBuilder.setForest(forest);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * Helper method to send DeleteForestResponse
     */
    private void sendDeleteForestResponse(boolean success, String message,
                                          StreamObserver<DeleteForestResponse> responseObserver) {
        DeleteForestResponse response = DeleteForestResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
