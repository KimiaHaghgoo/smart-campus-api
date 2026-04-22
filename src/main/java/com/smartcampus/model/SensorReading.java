package com.smartcampus.model;

import java.util.UUID;

// Represents a single recorded measurement from a sensor.
// Every time a reading is posted, a new instance is created with
public class SensorReading {

    private String id;
    private long timestamp;
    private double value;

    public SensorReading() {}

// This constructor is used when a new reading comes in from a POST request.    
    public SensorReading(double value) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.value = value;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}