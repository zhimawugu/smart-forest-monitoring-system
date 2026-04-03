package com.nci.forest.client.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Forest model for JavaFX TableView
 */
public class ForestModel {
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty latitude;
    private final StringProperty longitude;

    public ForestModel(String id, String name, String latitude, String longitude) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.latitude = new SimpleStringProperty(latitude);
        this.longitude = new SimpleStringProperty(longitude);
    }

    public String getId() {
        return id.get();
    }

    public String getName() {
        return name.get();
    }

    public String getLatitude() {
        return latitude.get();
    }

    public String getLongitude() {
        return longitude.get();
    }


    // Override toString to display Forest name in ComboBox
    @Override
    public String toString() {
        return name.get();
    }
}
