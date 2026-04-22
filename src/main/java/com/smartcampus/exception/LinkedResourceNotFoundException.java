package com.smartcampus.exception;

// Thrown when a sensor is posted with a roomId that doesn't exist.
public class LinkedResourceNotFoundException extends RuntimeException {

    public LinkedResourceNotFoundException(String message) {
        super(message);
    }
}