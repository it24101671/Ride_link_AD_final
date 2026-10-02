package com.ridelink.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class DriverCreateRequest {

    @NotNull(message = "userId is required")
    @Positive(message = "userId must be positive")
    private Long userId;

    @NotBlank(message = "licenseNumber is required")
    @Size(max = 50, message = "licenseNumber cannot exceed 50 characters")
    private String licenseNumber;

    @NotBlank(message = "serviceArea is required")
    @Size(max = 100, message = "serviceArea cannot exceed 100 characters")
    private String serviceArea;

    public DriverCreateRequest() {
    }

    public DriverCreateRequest(Long userId, String licenseNumber, String serviceArea) {
        this.userId = userId;
        this.licenseNumber = licenseNumber;
        this.serviceArea = serviceArea;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }
}
