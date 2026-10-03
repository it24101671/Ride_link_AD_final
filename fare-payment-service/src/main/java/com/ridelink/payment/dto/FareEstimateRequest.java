package com.ridelink.payment.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class FareEstimateRequest {

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Destination location is required")
    private String destinationLocation;

    private BigDecimal distanceKm;
    private Integer durationMinutes;

    public FareEstimateRequest() {
    }

    public FareEstimateRequest(String pickupLocation, String destinationLocation) {
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
    }

    public FareEstimateRequest(String pickupLocation, String destinationLocation, BigDecimal distanceKm, Integer durationMinutes) {
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
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
