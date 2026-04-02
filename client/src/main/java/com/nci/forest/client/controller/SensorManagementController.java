package com.nci.forest.client.controller;

import com.nci.forest.client.model.ForestModel;
import com.nci.forest.client.model.SensorModel;
import com.nci.forest.client.service.ForestGrpcClient;
import com.nci.forest.client.service.SensorGrpcClient;
import com.nci.forest.proto.Forest;
import com.nci.forest.proto.Sensor;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sensor Management Controller
 * Handles adding and removing sensors for forests
 */
public class SensorManagementController {
    @FXML
    private ComboBox<ForestModel> forestComboBox;

    @FXML
    private TableView<SensorModel> sensorsTable;

    @FXML
    private TableColumn<SensorModel, String> idColumn;
    @FXML
    private TableColumn<SensorModel, String> nameColumn;
    @FXML
    private TableColumn<SensorModel, Number> latitudeColumn;
    @FXML
    private TableColumn<SensorModel, Number> longitudeColumn;
    @FXML
    private TableColumn<SensorModel, String> createdAtColumn;
    @FXML
    private TableColumn<SensorModel, String> actionColumn;

    @FXML
    private Button addSensorButton;
    @FXML
    private Button refreshButton;

    @FXML
    private Label statusLabel;

    private ForestGrpcClient forestGrpcClient;
    private SensorGrpcClient sensorGrpcClient;
    private ForestModel selectedForest;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        // Initialize gRPC clients
        forestGrpcClient = new ForestGrpcClient();
        sensorGrpcClient = new SensorGrpcClient();

        // Setup table columns
        idColumn.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        latitudeColumn.setCellValueFactory(cellData -> cellData.getValue().latitudeProperty());
        longitudeColumn.setCellValueFactory(cellData -> cellData.getValue().longitudeProperty());
        createdAtColumn.setCellValueFactory(cellData -> cellData.getValue().createdAtProperty());

        // Setup action column with delete button
        setupActionColumn();

        // Load forests
        loadForests();
    }

    /**
     * Setup delete button in action column
     */
    private void setupActionColumn() {
        actionColumn.setCellFactory(column -> new TableCell<SensorModel, String>() {
            private final Button deleteButton = new Button("Delete");

            {
                deleteButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4px 8px;");
                deleteButton.setOnAction(event -> {
                    SensorModel sensor = getTableView().getItems().get(getIndex());
                    handleDeleteSensor(sensor);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                }
            }
        });
    }

    /**
     * Load all forests into combo box
     */
    private void loadForests() {
        new Thread(() -> {
            try {
                List<Forest> forests = forestGrpcClient.listForests();

                Platform.runLater(() -> {
                    forestComboBox.getItems().clear();
                    for (Forest forest : forests) {
                        ForestModel model = new ForestModel(
                                forest.getId(),
                                forest.getName(),
                                String.valueOf(forest.getLatitude()),
                                String.valueOf(forest.getLongitude()),
                                ""
                        );
                        forestComboBox.getItems().add(model);
                    }

                    if (!forestComboBox.getItems().isEmpty()) {
                        forestComboBox.getSelectionModel().selectFirst();
                        handleForestSelection();
                        statusLabel.setText("Loaded " + forests.size() + " forest(s)");
                    } else {
                        statusLabel.setText("No forests found. Create a forest first.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error loading forests: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Handle forest selection from combo box
     */
    @FXML
    private void handleForestSelection() {
        selectedForest = forestComboBox.getSelectionModel().getSelectedItem();

        if (selectedForest == null) {
            sensorsTable.getItems().clear();
            statusLabel.setText("No forest selected");
            return;
        }

        loadSensorsForForest();
    }

    /**
     * Load sensors for selected forest
     */
    private void loadSensorsForForest() {
        new Thread(() -> {
            try {
                List<Sensor> sensors = sensorGrpcClient.listSensors(selectedForest.getId());

                Platform.runLater(() -> {
                    sensorsTable.getItems().clear();

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
                                dateTime.format(dateFormatter)
                        );
                        sensorsTable.getItems().add(model);
                    }

                    statusLabel.setText("Loaded " + sensors.size() + " sensor(s) for " + selectedForest.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error loading sensors: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Handle add sensor button
     */
    @FXML
    private void handleAddSensor() {
        if (selectedForest == null) {
            showAlert("Error", "Please select a forest first");
            return;
        }

        // Create dialog for adding sensor
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Add Sensor");
        dialog.setHeaderText("Add a new sensor to " + selectedForest.getName());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Sensor Name (e.g., Sensor-001)");

        TextField latitudeField = new TextField();
        latitudeField.setPromptText("Latitude (e.g., 44.4280)");

        TextField longitudeField = new TextField();
        longitudeField.setPromptText("Longitude (e.g., -110.5885)");

        grid.add(new Label("Sensor Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Latitude:"), 0, 1);
        grid.add(latitudeField, 1, 1);
        grid.add(new Label("Longitude:"), 0, 2);
        grid.add(longitudeField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<Void> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                String name = nameField.getText().trim();
                String latStr = latitudeField.getText().trim();
                String lonStr = longitudeField.getText().trim();

                if (name.isEmpty() || latStr.isEmpty() || lonStr.isEmpty()) {
                    showAlert("Error", "All fields are required");
                    return;
                }

                double latitude = Double.parseDouble(latStr);
                double longitude = Double.parseDouble(lonStr);

                // Add sensor in background thread
                new Thread(() -> {
                    try {
                        sensorGrpcClient.addSensor(selectedForest.getId(), name, latitude, longitude);

                        Platform.runLater(() -> {
                            statusLabel.setText("Sensor added successfully!");
                            loadSensorsForForest();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Error adding sensor: " + e.getMessage());
                            showAlert("Error", "Failed to add sensor: " + e.getMessage());
                        });
                    }
                }).start();

            } catch (NumberFormatException e) {
                showAlert("Error", "Latitude and Longitude must be valid numbers");
            }
        }
    }

    /**
     * Handle delete sensor
     */
    private void handleDeleteSensor(SensorModel sensor) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Sensor");
        alert.setContentText("Are you sure you want to delete sensor \"" + sensor.getName() + "\"?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    sensorGrpcClient.removeSensor(sensor.getId());

                    Platform.runLater(() -> {
                        statusLabel.setText("Sensor deleted successfully!");
                        loadSensorsForForest();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Error deleting sensor: " + e.getMessage());
                        showAlert("Error", "Failed to delete sensor: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    /**
     * Handle refresh button
     */
    @FXML
    private void handleRefresh() {
        if (selectedForest != null) {
            loadSensorsForForest();
        } else {
            loadForests();
        }
    }

    /**
     * Show alert dialog
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

