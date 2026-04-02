package com.nci.forest.client.service;

import com.nci.forest.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Client Service for Forest Management
 */
public class ForestGrpcClient {
    private static final long DEFAULT_DEADLINE_SECONDS = 5;
    private static final String SERVICE_TYPE = "_forest-grpc._tcp.local.";

    private final ManagedChannel channel;
    private final ForestServiceGrpc.ForestServiceBlockingStub blockingStub;
    private final ForestServiceGrpc.ForestServiceStub asyncStub;
    private final long deadlineSeconds = DEFAULT_DEADLINE_SECONDS;

    public ForestGrpcClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve(SERVICE_TYPE);
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.blockingStub = ForestServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ForestServiceGrpc.newStub(channel);
    }

    public AddForestResponse addForest(String name, double latitude, double longitude, String address) {
        try {
            AddForestRequest request = AddForestRequest.newBuilder()
                    .setName(name)
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .build();

            ForestServiceGrpc.ForestServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            return stubWithDeadline.addForest(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus() == io.grpc.Status.DEADLINE_EXCEEDED) {
                throw new RuntimeException("Request timeout: exceeded " + deadlineSeconds + " seconds", e);
            }
            if (e.getStatus() == io.grpc.Status.CANCELLED) {
                throw new RuntimeException("Request was cancelled", e);
            }
            throw new RuntimeException("Failed to add forest: " + e.getMessage(), e);
        }
    }

    public DeleteForestResponse deleteForests(List<DeleteForestRequest> requests) {
        CompletableFuture<DeleteForestResponse> responseFuture = new CompletableFuture<>();

        StreamObserver<DeleteForestResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(DeleteForestResponse response) {
                responseFuture.complete(response);
            }

            @Override
            public void onError(Throwable t) {
                responseFuture.completeExceptionally(
                        new RuntimeException("Failed to delete forest(s): " + t.getMessage(), t));
            }

            @Override
            public void onCompleted() {}
        };

        try {
            ForestServiceGrpc.ForestServiceStub asyncStubWithDeadline = 
                asyncStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            StreamObserver<DeleteForestRequest> requestObserver = asyncStubWithDeadline.deleteForest(responseObserver);

            for (DeleteForestRequest request : requests) {
                requestObserver.onNext(request);
            }
            requestObserver.onCompleted();

            return responseFuture.get(deadlineSeconds + 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete forest(s): " + e.getMessage(), e);
        }
    }

    public List<Forest> listForests() {
        try {
            ListForestsRequest request = ListForestsRequest.newBuilder().build();
            ForestServiceGrpc.ForestServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            ListForestsResponse response = stubWithDeadline.listForests(request);
            return new ArrayList<>(response.getForestsList());
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("Failed to list forests: " + e.getMessage(), e);
        }
    }
}
