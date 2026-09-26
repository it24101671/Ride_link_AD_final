package com.ridelink.driver.dto;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;

import java.math.BigDecimal;

public class EligibleDriverResponse {

    private Long driverId;
    private Long userId;
    private String serviceArea;
    private AvailabilityStatus availability;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;

    public EligibleDriverResponse() {
    }

    public EligibleDriverResponse(Long driverId, Long userId, String serviceArea,
                                  AvailabilityStatus availability, BigDecimal currentLatitude, BigDecimal currentLongitude) {
        this.driverId = driverId;
        this.userId = userId;
        this.serviceArea = serviceArea;
        this.availability = availability;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
    }

    public static EligibleDriverResponse fromEntity(Driver driver) {
        return new EligibleDriverResponse(
                driver.getId(),
                driver.getUserId(),
                driver.getServiceArea(),
                driver.getAvailability(),
                driver.getCurrentLatitude(),
                driver.getCurrentLongitude()
        );
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public AvailabilityStatus getAvailability() {
        return availability;
    }

    public void setAvailability(AvailabilityStatus availability) {
        this.availability = availability;
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
}
