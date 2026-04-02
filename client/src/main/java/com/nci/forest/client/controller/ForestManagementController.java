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
                String name = nameField.getText();
                String lat = latField.getText();
                String lon = lonField.getText();
                String address = addressField.getText();

                return new ForestModel("", name, lat, lon, address);
            }
            return null;
        });

        Optional<ForestModel> result = dialog.showAndWait();
        result.ifPresent(forest -> {
            try {
                // Call gRPC service - let server handle validation
                var response = grpcClient.addForest(
                        forest.getName(),
                        Double.parseDouble(forest.getLatitude()),
                        Double.parseDouble(forest.getLongitude()),
                        forest.getAddress()
                );

                if (response.getSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", response.getMessage());
                    loadForests();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Failed", response.getMessage());
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Invalid coordinate format: " + e.getMessage());
            } catch (io.grpc.StatusRuntimeException e) {
                // Extract detailed error message from gRPC Status
                String errorMessage = e.getStatus().getDescription();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = e.getStatus().getCode() + ": " + e.getMessage();
                }
                showAlert(Alert.AlertType.ERROR, "Error", errorMessage);
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to add forest: " + e.getMessage());
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
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select at least one forest to delete");
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
                            showAlert(Alert.AlertType.INFORMATION, "Success", response.getMessage());
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Failed", response.getMessage());
                        }
                        loadForests();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete forests: " + e.getMessage());
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
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load forests: " + e.getMessage());
        }
    }

    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
