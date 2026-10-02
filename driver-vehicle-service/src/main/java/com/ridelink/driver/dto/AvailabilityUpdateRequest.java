package com.ridelink.driver.dto;

import com.ridelink.driver.entity.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public class AvailabilityUpdateRequest {

    @NotNull(message = "availability is required (AVAILABLE, UNAVAILABLE)")
    private AvailabilityStatus availability;

    public AvailabilityUpdateRequest() {
    }

    public AvailabilityUpdateRequest(AvailabilityStatus availability) {
        this.availability = availability;
    }

    public AvailabilityStatus getAvailability() {
        return availability;
    }

    public void setAvailability(AvailabilityStatus availability) {
        this.availability = availability;
    }
}
