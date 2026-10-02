package com.ridelink.driver.dto;

import com.ridelink.driver.entity.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class VehicleRequest {

    @NotBlank(message = "vehicleNumber is required")
    @Size(max = 30, message = "vehicleNumber cannot exceed 30 characters")
    private String vehicleNumber;

    @NotNull(message = "vehicleType is required (CAR, VAN, SUV, OTHER)")
    private VehicleType vehicleType;

    @NotBlank(message = "model is required")
    @Size(max = 80, message = "model cannot exceed 80 characters")
    private String model;

    @NotBlank(message = "color is required")
    @Size(max = 40, message = "color cannot exceed 40 characters")
    private String color;

    @NotNull(message = "year is required")
    @Min(value = 1990, message = "year must be 1990 or newer")
    @Max(value = 2035, message = "year cannot exceed valid registration range")
    private Integer year;

    public VehicleRequest() {
    }

    public VehicleRequest(String vehicleNumber, VehicleType vehicleType, String model, String color, Integer year) {
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.model = model;
        this.color = color;
        this.year = year;
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
