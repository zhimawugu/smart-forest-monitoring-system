package com.nci.forest.client.service;

import com.nci.forest.proto.SensorServiceGrpc;
import com.nci.forest.proto.StreamTemperatureRequest;
import com.nci.forest.proto.TemperatureData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Context;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
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

    /**
     * Client for streaming temperature data from sensors
     */
    public TemperatureDataStreamClient() {
        GrpcServiceDiscovery.Endpoint endpoint = GrpcServiceDiscovery.resolve(SERVICE_TYPE);
        this.channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        this.asyncStub = SensorServiceGrpc.newStub(channel);
    }

    /**
     * Start streaming temperature data from server for a specific sensor
     * @return CancellableContext that can be used to cancel the stream
     */
    public Context.CancellableContext startStreamingTemperatureData(String sensorId, String forestId, Consumer<TemperatureData> dataCallback) {
        Context.CancellableContext cancellableContext = Context.current().withCancellation();

        StreamTemperatureRequest request = StreamTemperatureRequest.newBuilder()
                .setSensorId(sensorId)
                .setForestId(forestId)
                .build();

        ClientResponseObserver<StreamTemperatureRequest, TemperatureData> responseObserver = 
            new ClientResponseObserver<>() {
                @Override
                public void beforeStart(ClientCallStreamObserver<StreamTemperatureRequest> requestStream) {
                    // No setup needed
                }

                @Override
                public void onNext(TemperatureData temperatureData) {
                    dataCallback.accept(temperatureData);
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("Error in temperature stream: {}", t.getMessage());
                }

                @Override
                public void onCompleted() {}
            };

        cancellableContext.run(() -> asyncStub.streamTemperatureData(request, responseObserver));

        return cancellableContext;
    }
}
