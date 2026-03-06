package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Constructor with custom host and port
     */
    public ForestGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = ForestServiceGrpc.newBlockingStub(channel);
        logger.info("gRPC client connected to " + host + ":" + port);
    }

    /**
     * Constructor with default localhost:50051
     */
    public ForestGrpcClient() {
        this("localhost", 50051);
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
     * Delete a forest by ID
     */
    public DeleteForestResponse deleteForest(String forestId) {
        try {
            DeleteForestRequest request = DeleteForestRequest.newBuilder()
                    .setId(forestId)
                    .build();

            DeleteForestResponse response = blockingStub.deleteForest(request);
            logger.info("DeleteForest response: " + response.getMessage());
            return response;
        } catch (StatusRuntimeException e) {
            logger.warning("RPC failed: " + e.getStatus());
            throw new RuntimeException("Failed to delete forest: " + e.getMessage(), e);
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

