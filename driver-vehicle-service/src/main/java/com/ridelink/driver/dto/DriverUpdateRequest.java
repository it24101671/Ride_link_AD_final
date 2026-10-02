package com.ridelink.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DriverUpdateRequest {

    @NotBlank(message = "licenseNumber is required")
    @Size(max = 50, message = "licenseNumber cannot exceed 50 characters")
    private String licenseNumber;

    @NotBlank(message = "serviceArea is required")
    @Size(max = 100, message = "serviceArea cannot exceed 100 characters")
    private String serviceArea;

    public DriverUpdateRequest() {
    }

    public DriverUpdateRequest(String licenseNumber, String serviceArea) {
        this.licenseNumber = licenseNumber;
        this.serviceArea = serviceArea;
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
