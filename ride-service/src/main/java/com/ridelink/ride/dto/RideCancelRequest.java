package com.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public class RideCancelRequest {

    @Size(max = 255, message = "Cancellation reason cannot exceed 255 characters")
    @Schema(description = "Reason for ride cancellation", example = "Passenger requested cancellation due to change of plans")
    private String reason;

    public RideCancelRequest() {
    }

    public RideCancelRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
