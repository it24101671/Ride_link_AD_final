package com.ridelink.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class FareCalculationRequest {

    @NotNull(message = "Ride ID is required")
    private Long rideId;

    private String pickupLocation;
    private String destinationLocation;
    private BigDecimal distanceKm;
    private Integer durationMinutes;

    public FareCalculationRequest() {
    }

    public FareCalculationRequest(Long rideId) {
        this.rideId = rideId;
    }

    public FareCalculationRequest(Long rideId, String pickupLocation, String destinationLocation) {
        this.rideId = rideId;
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(String destinationLocation) {
        this.destinationLocation = destinationLocation;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
