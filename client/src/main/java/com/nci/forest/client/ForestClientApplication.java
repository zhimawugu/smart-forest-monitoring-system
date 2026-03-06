package com.nci.forest.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX Application for Forest Monitoring Client
 */
public class ForestClientApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // Setup scene
        Scene scene = new Scene(root, 900, 600);

        // Setup stage
        primaryStage.setTitle("Forest Monitoring System - Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

