package com.ridelink.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class RideAssignRequest {

    @Schema(description = "Driver ID to assign. If omitted or null, an eligible available driver will be automatically selected.", example = "1")
    private Long driverId;

    public RideAssignRequest() {
    }

    public RideAssignRequest(Long driverId) {
        this.driverId = driverId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }
}
