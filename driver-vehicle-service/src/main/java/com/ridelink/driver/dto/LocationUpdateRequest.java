package com.ridelink.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class LocationUpdateRequest {

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be greater than or equal to -90.0")
    @DecimalMax(value = "90.0", message = "latitude must be less than or equal to 90.0")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be greater than or equal to -180.0")
    @DecimalMax(value = "180.0", message = "longitude must be less than or equal to 180.0")
    private BigDecimal longitude;

    public LocationUpdateRequest() {
    }

    public LocationUpdateRequest(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }
}
