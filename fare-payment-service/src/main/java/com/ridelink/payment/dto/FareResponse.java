package com.ridelink.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ridelink.payment.entity.Fare;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FareResponse {

    private Long id;
    private Long rideId;
    private String pickupLocation;
    private String destinationLocation;
    private BigDecimal baseFare;
    private BigDecimal distanceKm;
    private BigDecimal distanceAmount;
    private Integer durationMinutes;
    private BigDecimal timeAmount;
    private BigDecimal totalFare;
    private String currency;
    private String fareReference;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public FareResponse() {
    }

    public static FareResponse fromEntity(Fare fare) {
        FareResponse response = new FareResponse();
        response.setId(fare.getId());
        response.setRideId(fare.getRideId());
        response.setPickupLocation(fare.getPickupLocation());
        response.setDestinationLocation(fare.getDestinationLocation());
        response.setBaseFare(fare.getBaseAmount());
        response.setDistanceKm(fare.getDistanceKm());
        response.setDistanceAmount(fare.getDistanceAmount());
        response.setDurationMinutes(fare.getDurationMinutes());
        response.setTimeAmount(fare.getTimeAmount());
        response.setTotalFare(fare.getTotalAmount());
        response.setCurrency(fare.getCurrency());
        response.setFareReference(fare.getFareReference());
        response.setStatus(fare.getStatus().name());
        response.setCreatedAt(fare.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
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

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public BigDecimal getBaseAmount() {
        return baseFare;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getDistance() {
        return distanceKm;
    }

    public BigDecimal getDistanceAmount() {
        return distanceAmount;
    }

    public void setDistanceAmount(BigDecimal distanceAmount) {
        this.distanceAmount = distanceAmount;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getDuration() {
        return durationMinutes;
    }

    public BigDecimal getTimeAmount() {
        return timeAmount;
    }

    public void setTimeAmount(BigDecimal timeAmount) {
        this.timeAmount = timeAmount;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    public void setTotalFare(BigDecimal totalFare) {
        this.totalFare = totalFare;
    }

    public BigDecimal getTotalAmount() {
        return totalFare;
    }

    public BigDecimal getEstimatedFare() {
        return totalFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFareReference() {
        return fareReference;
    }

    public void setFareReference(String fareReference) {
        this.fareReference = fareReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
