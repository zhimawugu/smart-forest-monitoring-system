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
    private final StringProperty address;

    public ForestModel(String id, String name, String latitude, String longitude, String address) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.latitude = new SimpleStringProperty(latitude);
        this.longitude = new SimpleStringProperty(longitude);
        this.address = new SimpleStringProperty(address);
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

    // Latitude property
    public StringProperty latitudeProperty() {
        return latitude;
    }

    public String getLatitude() {
        return latitude.get();
    }

    // Longitude property
    public StringProperty longitudeProperty() {
        return longitude;
    }

    public String getLongitude() {
        return longitude.get();
    }

    // Address property
    public StringProperty addressProperty() {
        return address;
    }

    public String getAddress() {
        return address.get();
    }


    // Override toString to display Forest name in ComboBox
    @Override
    public String toString() {
        return name.get();
    }
}

