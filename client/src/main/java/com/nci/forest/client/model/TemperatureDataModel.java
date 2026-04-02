package com.nci.forest.client.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;

/**
 * Temperature Data model for real-time dashboard
 */
public class TemperatureDataModel {
    private final StringProperty sensorId;
    private final StringProperty sensorName;
    private final StringProperty forestId;
    private final DoubleProperty temperature;
    private final StringProperty timestamp;

    public TemperatureDataModel(String sensorId, String sensorName, String forestId,
                               double temperature, String timestamp) {
        this.sensorId = new SimpleStringProperty(sensorId);
        this.sensorName = new SimpleStringProperty(sensorName);
        this.forestId = new SimpleStringProperty(forestId);
        this.temperature = new SimpleDoubleProperty(temperature);
        this.timestamp = new SimpleStringProperty(timestamp);
    }

    // Sensor Name property
    public StringProperty sensorNameProperty() {
        return sensorName;
    }

    // Temperature property
    public DoubleProperty temperatureProperty() {
        return temperature;
    }

    public double getTemperature() {
        return temperature.get();
    }

    // Timestamp property
    public StringProperty timestampProperty() {
        return timestamp;
    }

    public String getTimestamp() {
        return timestamp.get();
    }
}
