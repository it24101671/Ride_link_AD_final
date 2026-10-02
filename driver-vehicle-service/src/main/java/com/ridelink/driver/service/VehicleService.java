package com.ridelink.driver.service;

import com.ridelink.driver.dto.VehicleRequest;
import com.ridelink.driver.dto.VehicleResponse;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.exception.DriverNotFoundException;
import com.ridelink.driver.exception.DuplicateResourceException;
import com.ridelink.driver.exception.VehicleNotFoundException;
import com.ridelink.driver.repository.DriverRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    public VehicleService(VehicleRepository vehicleRepository, DriverRepository driverRepository) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional
    public VehicleResponse addVehicle(Long driverId, VehicleRequest request, UserPrincipal currentUser) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + driverId));

        validateOwnerOrAdmin(driver.getUserId(), currentUser);

        if (vehicleRepository.existsByVehicleNumberIgnoreCase(request.getVehicleNumber().trim())) {
            throw new DuplicateResourceException("Vehicle number already registered: " + request.getVehicleNumber().trim());
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setDriver(driver);
        vehicle.setVehicleNumber(request.getVehicleNumber().trim().toUpperCase());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setModel(request.getModel().trim());
        vehicle.setColor(request.getColor().trim());
        vehicle.setYear(request.getYear());

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getVehiclesByDriver(Long driverId) {
        if (!driverRepository.existsById(driverId)) {
            throw new DriverNotFoundException("Driver not found with id: " + driverId);
        }
        return vehicleRepository.findByDriverId(driverId).stream()
                .map(VehicleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found with id: " + vehicleId));
        return VehicleResponse.fromEntity(vehicle);
    }

    @Transactional
    public VehicleResponse updateVehicle(Long vehicleId, VehicleRequest request, UserPrincipal currentUser) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found with id: " + vehicleId));

        validateOwnerOrAdmin(vehicle.getDriver().getUserId(), currentUser);

        String trimmedNum = request.getVehicleNumber().trim().toUpperCase();
        if (!vehicle.getVehicleNumber().equalsIgnoreCase(trimmedNum)) {
            if (vehicleRepository.existsByVehicleNumberIgnoreCase(trimmedNum)) {
                throw new DuplicateResourceException("Vehicle number already registered: " + trimmedNum);
            }
            vehicle.setVehicleNumber(trimmedNum);
        }

        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setModel(request.getModel().trim());
        vehicle.setColor(request.getColor().trim());
        vehicle.setYear(request.getYear());

        Vehicle updated = vehicleRepository.save(vehicle);
        return VehicleResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteVehicle(Long vehicleId, UserPrincipal currentUser) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found with id: " + vehicleId));

        validateOwnerOrAdmin(vehicle.getDriver().getUserId(), currentUser);

        vehicleRepository.delete(vehicle);
    }

    private void validateOwnerOrAdmin(Long targetUserId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        boolean isOwner = currentUser.getUserId().equals(targetUserId);
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Access denied: You are not authorized to manage this vehicle");
        }
    }
}
