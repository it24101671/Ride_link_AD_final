package com.ridelink.driver.dto;

import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleType;

public class VehicleResponse {

    private Long id;
    private Long driverId;
    private String vehicleNumber;
    private VehicleType vehicleType;
    private String model;
    private String color;
    private Integer year;

    public VehicleResponse() {
    }

    public VehicleResponse(Long id, Long driverId, String vehicleNumber, VehicleType vehicleType,
                           String model, String color, Integer year) {
        this.id = id;
        this.driverId = driverId;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.model = model;
        this.color = color;
        this.year = year;
    }

    public static VehicleResponse fromEntity(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getDriver() != null ? vehicle.getDriver().getId() : null,
                vehicle.getVehicleNumber(),
                vehicle.getVehicleType(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getYear()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}
