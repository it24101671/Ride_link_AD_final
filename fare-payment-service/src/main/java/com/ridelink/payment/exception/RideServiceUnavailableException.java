package com.ridelink.payment.exception;

public class RideServiceUnavailableException extends RuntimeException {
    public RideServiceUnavailableException(String message) {
        super(message);
    }
}
