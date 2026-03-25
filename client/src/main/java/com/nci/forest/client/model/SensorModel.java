package com.nci.forest.client.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;

/**
 * Sensor model for JavaFX TableView
 */
public class SensorModel {
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty forestId;
    private final DoubleProperty latitude;
    private final DoubleProperty longitude;
    private final StringProperty createdAt;

    public SensorModel(String id, String name, String forestId, double latitude, double longitude, String createdAt) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.forestId = new SimpleStringProperty(forestId);
        this.latitude = new SimpleDoubleProperty(latitude);
        this.longitude = new SimpleDoubleProperty(longitude);
        this.createdAt = new SimpleStringProperty(createdAt);
    }

    // ID property
    public StringProperty idProperty() {
        return id;
    }

    public String getId() {
        return id.get();
    }

    // Name property
    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    // Forest ID property
    public StringProperty forestIdProperty() {
        return forestId;
    }

    public String getForestId() {
        return forestId.get();
    }

    // Latitude property
    public DoubleProperty latitudeProperty() {
        return latitude;
    }

    public double getLatitude() {
        return latitude.get();
    }

    // Longitude property
    public DoubleProperty longitudeProperty() {
        return longitude;
    }

    public double getLongitude() {
        return longitude.get();
    }

    // Created At property
    public StringProperty createdAtProperty() {
        return createdAt;
    }

    public String getCreatedAt() {
        return createdAt.get();
    }

    // Override toString to display Sensor name in ComboBox
    @Override
    public String toString() {
        return name.get();
    }
}


