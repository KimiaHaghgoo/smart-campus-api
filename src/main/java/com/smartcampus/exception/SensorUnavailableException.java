package com.smartcampus.exception;

// Thrown when a POST reading is attempted on a sensor that is currently
// in MAINTENANCE status.
public class SensorUnavailableException extends RuntimeException {

    public SensorUnavailableException(String message) {
        super(message);
    }
}