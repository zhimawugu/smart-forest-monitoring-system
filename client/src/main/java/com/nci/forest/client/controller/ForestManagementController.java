package com.nci.forest.client.controller;

import com.nci.forest.client.model.ForestModel;
import com.nci.forest.client.service.ForestGrpcClient;
import com.nci.forest.proto.Forest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.Optional;

/**
 * Controller for Forest Management Tab
 */
public class ForestManagementController {

    @FXML
    private TableView<ForestModel> forestTable;

    @FXML
    private TableColumn<ForestModel, String> idColumn;

    @FXML
    private TableColumn<ForestModel, String> nameColumn;

    @FXML
    private TableColumn<ForestModel, String> latitudeColumn;

    @FXML
    private TableColumn<ForestModel, String> longitudeColumn;

    @FXML
    private TableColumn<ForestModel, String> addressColumn;

    @FXML
    private Button addButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button refreshButton;

    private final ObservableList<ForestModel> forestList = FXCollections.observableArrayList();
    private ForestGrpcClient grpcClient;

    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        // Initialize gRPC client
        grpcClient = new ForestGrpcClient();

        // Enable multiple selection in table
        forestTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Setup table columns
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        latitudeColumn.setCellValueFactory(new PropertyValueFactory<>("latitude"));
        longitudeColumn.setCellValueFactory(new PropertyValueFactory<>("longitude"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));

        // Bind data to table
        forestTable.setItems(forestList);

        // Load initial data
        loadForests();
    }

    /**
     * Handle Add Forest button click
     */
    @FXML
    private void handleAddForest() {
        // Create dialog for input
        Dialog<ForestModel> dialog = new Dialog<>();
        dialog.setTitle("Add New Forest");
        dialog.setHeaderText("Enter forest information");

        // Set button types
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create input fields
        TextField nameField = new TextField();
        nameField.setPromptText("Forest Name");

        TextField latField = new TextField();
        latField.setPromptText("Latitude (e.g., 44.4280)");

        TextField lonField = new TextField();
        lonField.setPromptText("Longitude (e.g., -110.5885)");

        TextField addressField = new TextField();
        addressField.setPromptText("Address (optional)");

        // Layout
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Latitude:"), 0, 1);
        grid.add(latField, 1, 1);
        grid.add(new Label("Longitude:"), 0, 2);
        grid.add(lonField, 1, 2);
        grid.add(new Label("Address:"), 0, 3);
        grid.add(addressField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Request focus on name field
        Platform.runLater(() -> nameField.requestFocus());

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    String name = nameField.getText();
                    double lat = Double.parseDouble(latField.getText());
                    double lon = Double.parseDouble(lonField.getText());
                    String address = addressField.getText();

                    return new ForestModel("", name, String.valueOf(lat), String.valueOf(lon), address);
                } catch (NumberFormatException e) {
                    showError("Invalid Input", "Please enter valid numbers for latitude and longitude");
                    return null;
                }
            }
            return null;
        });

        Optional<ForestModel> result = dialog.showAndWait();
        result.ifPresent(forest -> {
            try {
                // Call gRPC service
                var response = grpcClient.addForest(
                        forest.getName(),
                        Double.parseDouble(forest.getLatitude()),
                        Double.parseDouble(forest.getLongitude()),
                        forest.getAddress()
                );

                if (response.getSuccess()) {
                    showInfo("Success", response.getMessage());
                    loadForests();
                } else {
                    showError("Failed", response.getMessage());
                }
            } catch (Exception e) {
                showError("Error", "Failed to add forest: " + e.getMessage());
            }
        });
    }


    /**
     * Handle Batch Delete button click
     */
    @FXML
    private void handleBatchDelete() {
        // Get all selected items
        ObservableList<ForestModel> selectedItems = forestTable.getSelectionModel().getSelectedItems();

        if (selectedItems == null || selectedItems.isEmpty()) {
            showWarning("No Selection", "Please select at least one forest to delete");
            return;
        }

        // Build confirmation message
        StringBuilder forestNames = new StringBuilder();
        for (int i = 0; i < selectedItems.size(); i++) {
            if (i > 0) forestNames.append(", ");
            forestNames.append(selectedItems.get(i).getName());
        }

        // Confirm deletion
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Batch Deletion");
        confirmAlert.setHeaderText("Delete Multiple Forests");
        confirmAlert.setContentText(String.format("Are you sure you want to delete %d forest(s)?\n\n%s",
                selectedItems.size(), forestNames.toString()));

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    // Build delete requests
                    java.util.List<com.nci.forest.proto.DeleteForestRequest> requests = new java.util.ArrayList<>();
                    for (ForestModel forest : selectedItems) {
                        requests.add(com.nci.forest.proto.DeleteForestRequest.newBuilder()
                                .setId(forest.getId())
                                .build());
                    }

                    // Send batch delete request
                    var response = grpcClient.deleteForests(requests);

                    Platform.runLater(() -> {
                        if (response.getSuccess()) {
                            showInfo("Success", response.getMessage());
                        } else {
                            showError("Failed", response.getMessage());
                        }
                        loadForests();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showError("Error", "Failed to delete forests: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    /**
     * Handle Refresh button click
     */
    @FXML
    private void handleRefresh() {
        loadForests();
    }

    /**
     * Load forests from server
     */
    private void loadForests() {
        try {
            var forests = grpcClient.listForests();
            forestList.clear();

            for (Forest forest : forests) {
                ForestModel model = new ForestModel(
                        forest.getId(),
                        forest.getName(),
                        String.valueOf(forest.getLatitude()),
                        String.valueOf(forest.getLongitude()),
                        ""
                );
                forestList.add(model);
            }
        } catch (Exception e) {
            showError("Error", "Failed to load forests: " + e.getMessage());
        }
    }

    /**
     * Show info alert
     */
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show warning alert
     */
    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show error alert
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Cleanup when controller is destroyed
     */
    public void shutdown() {
        try {
            if (grpcClient != null) {
                grpcClient.shutdown();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

