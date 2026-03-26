package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * gRPC Client Service for Forest Management
 * Handles communication with the gRPC server
 */
public class ForestGrpcClient {
    private static final Logger logger = Logger.getLogger(ForestGrpcClient.class.getName());

    private final ManagedChannel channel;
    private final ForestServiceGrpc.ForestServiceBlockingStub blockingStub;
    private final ForestServiceGrpc.ForestServiceStub asyncStub;

    /**
     * Constructor with custom host and port
     */
    public ForestGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = ForestServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ForestServiceGrpc.newStub(channel);
        logger.info("gRPC client connected to " + host + ":" + port);
    }

    /**
     * Constructor with default localhost:50051
     */
    public ForestGrpcClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve();
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.blockingStub = ForestServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ForestServiceGrpc.newStub(channel);
        logger.info("gRPC client connected to " + endpoint.host() + ":" + endpoint.port());
    }

    /**
     * Add a new forest
     */
    public AddForestResponse addForest(String name, double latitude, double longitude, String address) {
        try {
            Location location = Location.newBuilder()
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .setAddress(address)
                    .build();

            AddForestRequest request = AddForestRequest.newBuilder()
                    .setName(name)
                    .setLocation(location)
                    .build();

            AddForestResponse response = blockingStub.addForest(request);
            logger.info("AddForest response: " + response.getMessage());
            return response;
        } catch (StatusRuntimeException e) {
            logger.warning("RPC failed: " + e.getStatus());
            throw new RuntimeException("Failed to add forest: " + e.getMessage(), e);
        }
    }

    /**
     * Delete multiple forests using client-side streaming
     */
    public DeleteForestResponse deleteForests(List<DeleteForestRequest> requests) {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final DeleteForestResponse[] responseHolder = new DeleteForestResponse[1];
        final Exception[] exceptionHolder = new Exception[1];

        StreamObserver<DeleteForestResponse> responseObserver = new StreamObserver<DeleteForestResponse>() {
            @Override
            public void onNext(DeleteForestResponse response) {
                logger.info("DeleteForest response: " + response.getMessage());
                responseHolder[0] = response;
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("DeleteForest failed: " + t.getMessage());
                exceptionHolder[0] = new RuntimeException("Failed to delete forest(s): " + t.getMessage(), t);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("DeleteForest stream completed");
                finishLatch.countDown();
            }
        };

        try {
            StreamObserver<DeleteForestRequest> requestObserver = asyncStub.deleteForest(responseObserver);

            // Send all requests
            for (DeleteForestRequest request : requests) {
                logger.info("Sending DeleteForest request: id=" + request.getId());
                requestObserver.onNext(request);
            }

            // Mark the end of requests
            requestObserver.onCompleted();

            // Wait for response
            if (!finishLatch.await(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("DeleteForest request timed out");
            }

            if (exceptionHolder[0] != null) {
                throw exceptionHolder[0];
            }

            return responseHolder[0];

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DeleteForest request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete forest(s): " + e.getMessage(), e);
        }
    }

    /**
     * List all forests
     */
    public List<Forest> listForests() {
        try {
            ListForestsRequest request = ListForestsRequest.newBuilder().build();
            ListForestsResponse response = blockingStub.listForests(request);
            logger.info("Listed " + response.getTotal() + " forests");
            return new ArrayList<>(response.getForestsList());
        } catch (StatusRuntimeException e) {
            logger.warning("RPC failed: " + e.getStatus());
            throw new RuntimeException("Failed to list forests: " + e.getMessage(), e);
        }
    }

    /**
     * Shutdown the channel
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        logger.info("gRPC client shutdown");
    }
}
