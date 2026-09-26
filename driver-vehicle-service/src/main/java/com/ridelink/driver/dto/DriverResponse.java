package com.ridelink.driver.dto;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.DriverStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DriverResponse {

    private Long id;
    private Long userId;
    private String licenseNumber;
    private AvailabilityStatus availability;
    private String serviceArea;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;
    private DriverStatus status;
    private List<VehicleResponse> vehicles;

    public DriverResponse() {
    }

    public DriverResponse(Long id, Long userId, String licenseNumber, AvailabilityStatus availability,
                          String serviceArea, BigDecimal currentLatitude, BigDecimal currentLongitude,
                          DriverStatus status, List<VehicleResponse> vehicles) {
        this.id = id;
        this.userId = userId;
        this.licenseNumber = licenseNumber;
        this.availability = availability;
        this.serviceArea = serviceArea;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.status = status;
        this.vehicles = vehicles;
    }

    public static DriverResponse fromEntity(Driver driver) {
        List<VehicleResponse> vehicleResponses = driver.getVehicles() != null
                ? driver.getVehicles().stream().map(VehicleResponse::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return new DriverResponse(
                driver.getId(),
                driver.getUserId(),
                driver.getLicenseNumber(),
                driver.getAvailability(),
                driver.getServiceArea(),
                driver.getCurrentLatitude(),
                driver.getCurrentLongitude(),
                driver.getStatus(),
                vehicleResponses
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public AvailabilityStatus getAvailability() {
        return availability;
    }

    public void setAvailability(AvailabilityStatus availability) {
        this.availability = availability;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public BigDecimal getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(BigDecimal currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public BigDecimal getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(BigDecimal currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public List<VehicleResponse> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<VehicleResponse> vehicles) {
        this.vehicles = vehicles;
    }
}
