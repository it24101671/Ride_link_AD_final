package com.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RideCreateRequest {

    @NotBlank(message = "Pickup location is required")
    @Size(max = 255, message = "Pickup location cannot exceed 255 characters")
    @Schema(description = "Pickup location or address", example = "Colombo Fort Railway Station")
    private String pickupLocation;

    @NotBlank(message = "Destination location is required")
    @Size(max = 255, message = "Destination location cannot exceed 255 characters")
    @Schema(description = "Destination location or address", example = "Bandaranaike International Airport, Katunayake")
    private String destinationLocation;

    public RideCreateRequest() {
    }

    public RideCreateRequest(String pickupLocation, String destinationLocation) {
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
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
}
