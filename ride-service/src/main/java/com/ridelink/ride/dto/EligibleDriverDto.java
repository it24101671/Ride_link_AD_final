package com.ridelink.ride.dto;

import java.math.BigDecimal;

public class EligibleDriverDto {

    private Long driverId;
    private Long userId;
    private String serviceArea;
    private String availability;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;

    public EligibleDriverDto() {
    }

    public EligibleDriverDto(Long driverId, Long userId, String serviceArea, String availability,
                             BigDecimal currentLatitude, BigDecimal currentLongitude) {
        this.driverId = driverId;
        this.userId = userId;
        this.serviceArea = serviceArea;
        this.availability = availability;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
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

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
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
