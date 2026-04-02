package com.nci.forest.client.controller;

import com.nci.forest.client.model.SensorModel;
import com.nci.forest.client.service.AlertGrpcClient;
import com.nci.forest.client.service.SensorGrpcClient;
import com.nci.forest.client.service.ForestGrpcClient;
import com.nci.forest.proto.AlertEvent;
import com.nci.forest.proto.Sensor;
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
 * Alert Management Controller
 * Manages sensor alert thresholds and displays alert events
 */
public class AlertManagementController {

    private static final Logger logger = LoggerFactory.getLogger(AlertManagementController.class);

    @FXML
    private ComboBox<SensorModel> sensorComboBox;

    @FXML
    private Spinner<Integer> maxTempSpinner;

    @FXML
    private Button setAlertButton;

    @FXML
    private Button clearAlertButton;

    @FXML
    private TableView<AlertEventModel> alertTable;

    @FXML
    private TableColumn<AlertEventModel, String> sensorNameColumn;

    @FXML
    private TableColumn<AlertEventModel, String> alertTypeColumn;

    @FXML
    private TableColumn<AlertEventModel, Number> tempColumn;

    @FXML
    private TableColumn<AlertEventModel, Number> thresholdColumn;

    @FXML
    private TableColumn<AlertEventModel, String> timeColumn;

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private Label selectedAlertLabel;

    private SensorGrpcClient sensorGrpcClient;
    private ForestGrpcClient forestGrpcClient;
    private AlertGrpcClient alertGrpcClient;
    private Map<String, SensorModel> sensorMap = new HashMap<>();
    private Map<String, AlertEvent> alertEventMap = new HashMap<>();
    private SensorModel selectedSensor;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        // Initialize gRPC clients
        sensorGrpcClient = new SensorGrpcClient();
        forestGrpcClient = new ForestGrpcClient();
        alertGrpcClient = new AlertGrpcClient();

        // Setup table columns
        sensorNameColumn.setCellValueFactory(cellData -> cellData.getValue().sensorNameProperty());
        alertTypeColumn.setCellValueFactory(cellData -> cellData.getValue().alertTypeProperty());
        tempColumn.setCellValueFactory(cellData -> cellData.getValue().temperatureProperty());
        thresholdColumn.setCellValueFactory(cellData -> cellData.getValue().thresholdProperty());
        timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

        // Initialize spinner (only max temperature)
        SpinnerValueFactory<Integer> maxSpinnerFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 30);
        maxTempSpinner.setValueFactory(maxSpinnerFactory);
        maxTempSpinner.setEditable(true);

        // Add text formatter to handle editable input properly
        maxTempSpinner.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                try {
                    int value = Integer.parseInt(newValue);
                    if (value >= 0 && value <= 100) {
                        maxSpinnerFactory.setValue(value);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });

        // Setup sensor combo box
        setupSensorComboBox();

        // Start alert watching
        startAlertWatcher();

        // Setup button handlers
        setAlertButton.setOnAction(event -> handleSetAlert());
        clearAlertButton.setOnAction(event -> handleClearAlert());

        // Setup sensor selection handler
        sensorComboBox.setOnAction(event -> handleSensorSelection());
    }

    /**
     * Setup sensor combo box with available sensors
     */
    private void setupSensorComboBox() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    sensorComboBox.getItems().clear();
                    connectionStatusLabel.setText("Loading sensors...");
                    connectionStatusLabel.setStyle("-fx-text-fill: orange;");
                });

                sensorMap.clear();

                List<com.nci.forest.proto.Forest> forests;
                try {
                    forests = forestGrpcClient.listForests();
                } catch (Exception e) {
                    logger.error("Failed to load forests: {}", e.getMessage());
                    forests = new ArrayList<>();
                }

                if (forests.isEmpty()) {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("No forests found. Please create a forest first.");
                        connectionStatusLabel.setStyle("-fx-text-fill: orange;");
                    });
                    return;
                }

                int totalSensors = 0;
                for (com.nci.forest.proto.Forest forest : forests) {
                    try {
                        List<Sensor> sensors = sensorGrpcClient.listSensors(forest.getId());
                        totalSensors += sensors.size();

                        for (Sensor sensor : sensors) {
                            SensorModel sensorModel = new SensorModel(
                                    sensor.getId(),
                                    sensor.getName() + " (" + forest.getName() + ")",
                                    sensor.getForestId(),
                                    sensor.getLatitude(),
                                    sensor.getLongitude(),
                                    String.valueOf(sensor.getCreatedAt())
                            );
                            sensorMap.put(sensor.getId(), sensorModel);

                            Platform.runLater(() -> {
                                if (!sensorComboBox.getItems().contains(sensorModel)) {
                                    sensorComboBox.getItems().add(sensorModel);
                                }
                            });
                        }
                    } catch (Exception e) {
                        logger.error("Could not load sensors for forest {}: {}", forest.getId(), e.getMessage());
                    }
                }

                final int loadedCount = totalSensors;
                Platform.runLater(() -> {
                    if (loadedCount == 0) {
                        connectionStatusLabel.setText("No sensors found. Please create a sensor first.");
                        connectionStatusLabel.setStyle("-fx-text-fill: orange;");
                    } else {
                        connectionStatusLabel.setText("Ready - Loaded " + loadedCount + " sensors");
                        connectionStatusLabel.setStyle("-fx-text-fill: green;");
                    }
                });
            } catch (Exception e) {
                logger.error("Error loading sensors for alert management: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Error: " + e.getMessage());
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }

    /**
     * Reload sensors
     */
    public void reloadSensors() {
        setupSensorComboBox();
    }

    /**
     * Start watching for alerts from server
     */
    private void startAlertWatcher() {
        new Thread(() -> {
            try {
                alertGrpcClient.startWatchingAlerts(new AlertGrpcClient.AlertEventCallback() {
                    @Override
                    public void onAlertReceived(AlertEvent event) {
                        handleAlertEvent(event);
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error("Alert stream error: {}", t.getMessage());
                        Platform.runLater(() -> {
                            connectionStatusLabel.setText("Disconnected: " + t.getMessage());
                            connectionStatusLabel.setStyle("-fx-text-fill: red;");
                        });
                    }

                    @Override
                    public void onCompleted() {
                        Platform.runLater(() -> {
                            connectionStatusLabel.setText("Disconnected");
                            connectionStatusLabel.setStyle("-fx-text-fill: orange;");
                        });
                    }
                });

                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: green;");
                });
            } catch (Exception e) {
                logger.error("Error starting alert watcher", e);
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connection Failed");
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }

    /**
     * Handle alert event received from server
     */
    private void handleAlertEvent(AlertEvent event) {
        alertEventMap.put(event.getAlertId(), event);

        AlertEventModel model = new AlertEventModel(
                event.getAlertId(),
                event.getSensorName(),
                event.getAlertType(),
                event.getCurrentTemperature(),
                event.getThreshold(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimestamp()),
                        ZoneId.systemDefault()).format(formatter)
        );

        Platform.runLater(() -> {
            alertTable.getItems().add(0, model);
            selectedAlertLabel.setText("Latest: " + event.getSensorName() +
                                      " - " + event.getAlertType() +
                                      " (" + event.getCurrentTemperature() + "°C)");
            showAlertNotification(event);
        });
    }

    /**
     * Show alert notification dialog
     */
    private void showAlertNotification(AlertEvent event) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("⚠️ Temperature Alert");
        alert.setHeaderText("Sensor: " + event.getSensorName());

        String message = String.format("Alert Type: %s\n" +
                        "Current Temperature: %.2f°C\n" +
                        "Threshold: %.2f°C\n" +
                        "Time: %s",
                event.getAlertType(),
                event.getCurrentTemperature(),
                event.getThreshold(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimestamp()),
                        ZoneId.systemDefault()).format(formatter)
        );

        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Handle set alert button click
     */
    private void handleSetAlert() {
        if (selectedSensor == null) {
            showAlert(Alert.AlertType.ERROR, "Please select a sensor first");
            return;
        }

        int maxTemp = maxTempSpinner.getValue();

        new Thread(() -> {
            try {
                alertGrpcClient.setAlertThreshold(selectedSensor.getId(), maxTemp);

                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Alert threshold set successfully for " + selectedSensor.getName() +
                            "\nMax Temperature: " + maxTemp + "°C");
                });
            } catch (Exception e) {
                logger.error("Error setting alert threshold", e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Failed to set alert threshold: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Handle clear alert button click
     */
    private void handleClearAlert() {
        if (selectedSensor == null) {
            showAlert(Alert.AlertType.ERROR, "Please select a sensor first");
            return;
        }

        new Thread(() -> {
            try {
                alertGrpcClient.setAlertThreshold(selectedSensor.getId(), 999);

                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Alert cleared for " + selectedSensor.getName());
                });
            } catch (Exception e) {
                logger.error("Error clearing alert", e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Failed to clear alert: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Handle sensor selection
     */
    private void handleSensorSelection() {
        selectedSensor = sensorComboBox.getSelectionModel().getSelectedItem();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "Error" : "Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Model class for alert events in table
     */
    public static class AlertEventModel {
        private String alertId;
        private javafx.beans.property.StringProperty sensorName;
        private javafx.beans.property.StringProperty alertType;
        private javafx.beans.property.DoubleProperty temperature;
        private javafx.beans.property.DoubleProperty threshold;
        private javafx.beans.property.StringProperty time;

        public AlertEventModel(String alertId, String sensorName, String alertType,
                               double temperature, double threshold, String time) {
            this.alertId = alertId;
            this.sensorName = new javafx.beans.property.SimpleStringProperty(sensorName);
            this.alertType = new javafx.beans.property.SimpleStringProperty(alertType);
            this.temperature = new javafx.beans.property.SimpleDoubleProperty(temperature);
            this.threshold = new javafx.beans.property.SimpleDoubleProperty(threshold);
            this.time = new javafx.beans.property.SimpleStringProperty(time);
        }

        public String getAlertId() {
            return alertId;
        }

        public javafx.beans.property.StringProperty sensorNameProperty() {
            return sensorName;
        }

        public javafx.beans.property.StringProperty alertTypeProperty() {
            return alertType;
        }

        public javafx.beans.property.DoubleProperty temperatureProperty() {
            return temperature;
        }

        public javafx.beans.property.DoubleProperty thresholdProperty() {
            return threshold;
        }

        public javafx.beans.property.StringProperty timeProperty() {
            return time;
        }
    }
}
