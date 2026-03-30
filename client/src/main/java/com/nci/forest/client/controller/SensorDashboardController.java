package com.nci.forest.client.controller;

import com.nci.forest.client.model.SensorModel;
import com.nci.forest.client.model.TemperatureDataModel;
import com.nci.forest.client.service.ForestGrpcClient;
import com.nci.forest.client.service.SensorGrpcClient;
import com.nci.forest.client.service.TemperatureDataStreamClient;
import com.nci.forest.proto.Forest;
import com.nci.forest.proto.Sensor;
import com.nci.forest.proto.TemperatureData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sensor Dashboard Controller
 * Displays real-time temperature data from selected sensor
 */
public class SensorDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(SensorDashboardController.class);

    @FXML
    private ComboBox<SensorModel> sensorComboBox;

    @FXML
    private TableView<TemperatureDataModel> temperatureTable;

    @FXML
    private TableColumn<TemperatureDataModel, String> timeColumn;

    @FXML
    private TableColumn<TemperatureDataModel, Number> tempColumn;

    @FXML
    private TableColumn<TemperatureDataModel, String> sensorNameColumn;

    @FXML
    private Label currentTempLabel;

    @FXML
    private Label avgTempLabel;

    @FXML
    private Label minMaxLabel;

    @FXML
    private Label lastUpdateLabel;

    @FXML
    private Label statusLabel;

    private SensorGrpcClient sensorGrpcClient;
    private ForestGrpcClient forestGrpcClient;
    private TemperatureDataStreamClient temperatureStreamClient;
    private SensorModel selectedSensor;
    private final List<TemperatureDataModel> temperatureDataList = new ArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        logger.info("Initializing Sensor Dashboard Controller");

        // Initialize gRPC clients
        sensorGrpcClient = new SensorGrpcClient();
        forestGrpcClient = new ForestGrpcClient();
        temperatureStreamClient = new TemperatureDataStreamClient();

        // Setup table columns
        timeColumn.setCellValueFactory(cellData -> cellData.getValue().timestampProperty());
        tempColumn.setCellValueFactory(cellData -> cellData.getValue().temperatureProperty());
        sensorNameColumn.setCellValueFactory(cellData -> cellData.getValue().sensorNameProperty());

        // Load sensors
        refreshSensorList();
    }

    /**
     * Load all sensors from all forests
     */
    private void refreshSensorList() {
        new Thread(() -> {
            try {
                logger.info("Refreshing sensor list");

                // Get all forests
                List<Forest> forests = forestGrpcClient.listForests();
                List<SensorModel> allSensors = new ArrayList<>();

                // For each forest, get its sensors
                for (Forest forest : forests) {
                    List<Sensor> sensors = sensorGrpcClient.listSensors(forest.getId());
                    for (Sensor sensor : sensors) {
                        LocalDateTime dateTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(sensor.getCreatedAt()),
                                ZoneId.systemDefault()
                        );

                        SensorModel model = new SensorModel(
                                sensor.getId(),
                                sensor.getName(),
                                sensor.getForestId(),
                                sensor.getLatitude(),
                                sensor.getLongitude(),
                                dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        );
                        allSensors.add(model);
                    }
                }

                // Update UI in FX thread
                Platform.runLater(() -> {
                    sensorComboBox.getItems().clear();
                    sensorComboBox.getItems().addAll(allSensors);

                    if (!allSensors.isEmpty()) {
                        sensorComboBox.getSelectionModel().selectFirst();
                        statusLabel.setText("Loaded " + allSensors.size() + " sensor(s)");
                    } else {
                        statusLabel.setText("No sensors found. Add sensors first.");
                    }
                });
            } catch (Exception e) {
                logger.error("Error loading sensors", e);
                Platform.runLater(() -> statusLabel.setText("Error loading sensors: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Handle sensor selection from combo box
     */
    @FXML
    private void handleSensorSelection() {
        selectedSensor = sensorComboBox.getSelectionModel().getSelectedItem();

        if (selectedSensor == null) {
            statusLabel.setText("No sensor selected");
            return;
        }

        // Clear previous data
        temperatureDataList.clear();
        temperatureTable.refresh();

        // Start new stream for selected sensor
        startTemperatureStream();

        statusLabel.setText("Connected to: " + selectedSensor.getName());
    }

    /**
     * Start streaming temperature data from selected sensor
     */
    private void startTemperatureStream() {
        // Request temperature stream from server
        temperatureStreamClient.startStreamingTemperatureData(
                selectedSensor.getId(),
                selectedSensor.getForestId(),
                this::onTemperatureDataReceived
        );

        logger.info("Started temperature stream for sensor: {}", selectedSensor.getName());
    }

    /**
     * Callback when temperature data is received
     */
    private void onTemperatureDataReceived(TemperatureData data) {
        Platform.runLater(() -> {
            try {
                // Create model from proto data
                LocalDateTime dateTime = LocalDateTime.now();
                String timestamp = dateTime.format(timeFormatter);

                TemperatureDataModel model = new TemperatureDataModel(
                        data.getSensorId(),
                        data.getSensorName(),
                        data.getForestId(),
                        data.getTemperature(),
                        timestamp
                );

                // Add to list (keep last 50 entries)
                temperatureDataList.add(0, model);
                if (temperatureDataList.size() > 50) {
                    temperatureDataList.remove(temperatureDataList.size() - 1);
                }

                temperatureTable.setItems(javafx.collections.FXCollections.observableArrayList(temperatureDataList));

                // Update statistics
                updateStatistics();

                logger.debug("Temperature received: {} °C", data.getTemperature());
            } catch (Exception e) {
                logger.error("Error processing temperature data", e);
            }
        });
    }

    /**
     * Update temperature statistics display
     */
    private void updateStatistics() {
        if (temperatureDataList.isEmpty()) {
            return;
        }

        // Current temperature (latest)
        TemperatureDataModel latest = temperatureDataList.get(0);
        currentTempLabel.setText(String.format("%.2f °C", latest.getTemperature()));
        lastUpdateLabel.setText("Last update: " + latest.getTimestamp());

        // Calculate average (last 10 readings)
        int count = Math.min(10, temperatureDataList.size());
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            double temp = temperatureDataList.get(i).getTemperature();
            sum += temp;
            min = Math.min(min, temp);
            max = Math.max(max, temp);
        }

        double average = sum / count;
        avgTempLabel.setText(String.format("%.2f °C", average));
        minMaxLabel.setText(String.format("%.2f / %.2f °C", min, max));
    }

    /**
     * Refresh sensors button handler
     */
    @FXML
    private void handleRefreshSensors() {
        refreshSensorList();
        statusLabel.setText("Sensors refreshed");
    }

    /**
     * Add sensor to combo box
     */
    public void addSensorToList(SensorModel sensor) {
        sensorComboBox.getItems().add(sensor);
    }

    /**
     * Cleanup on close
     */
    public void cleanup() {
        try {
            if (sensorGrpcClient != null) {
                sensorGrpcClient.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down gRPC client", e);
        }
    }
}
