package com.ridelink.driver.service;

import com.ridelink.driver.dto.*;
import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.DriverStatus;
import com.ridelink.driver.exception.DriverNotFoundException;
import com.ridelink.driver.exception.DuplicateResourceException;
import com.ridelink.driver.repository.DriverRepository;
import com.ridelink.driver.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverResponse createDriver(DriverCreateRequest request, UserPrincipal currentUser) {
        validateOwnerOrAdmin(request.getUserId(), currentUser);

        if (driverRepository.existsByUserId(request.getUserId())) {
            throw new DuplicateResourceException("Driver profile already exists for userId: " + request.getUserId());
        }

        if (driverRepository.existsByLicenseNumberIgnoreCase(request.getLicenseNumber().trim())) {
            throw new DuplicateResourceException("License number already registered: " + request.getLicenseNumber().trim());
        }

        Driver driver = new Driver();
        driver.setUserId(request.getUserId());
        driver.setLicenseNumber(request.getLicenseNumber().trim());
        driver.setServiceArea(request.getServiceArea().trim());
        driver.setAvailability(AvailabilityStatus.UNAVAILABLE);
        driver.setStatus(DriverStatus.ACTIVE);

        Driver saved = driverRepository.save(driver);
        return DriverResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));
        return DriverResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverByUserId(Long userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with userId: " + userId));
        return DriverResponse.fromEntity(driver);
    }

    @Transactional
    public DriverResponse updateDriver(Long id, DriverUpdateRequest request, UserPrincipal currentUser) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));

        validateOwnerOrAdmin(driver.getUserId(), currentUser);

        if (!driver.getLicenseNumber().equalsIgnoreCase(request.getLicenseNumber().trim())) {
            if (driverRepository.existsByLicenseNumberIgnoreCase(request.getLicenseNumber().trim())) {
                throw new DuplicateResourceException("License number already registered: " + request.getLicenseNumber().trim());
            }
            driver.setLicenseNumber(request.getLicenseNumber().trim());
        }

        driver.setServiceArea(request.getServiceArea().trim());
        Driver updated = driverRepository.save(driver);
        return DriverResponse.fromEntity(updated);
    }

    @Transactional
    public DriverResponse updateAvailability(Long id, AvailabilityUpdateRequest request, UserPrincipal currentUser) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));

        if (currentUser != null) {
            validateOwnerOrAdmin(driver.getUserId(), currentUser);
        }

        driver.setAvailability(request.getAvailability());
        Driver updated = driverRepository.save(driver);
        return DriverResponse.fromEntity(updated);
    }

    @Transactional
    public DriverResponse updateLocation(Long id, LocationUpdateRequest request, UserPrincipal currentUser) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));

        validateOwnerOrAdmin(driver.getUserId(), currentUser);

        driver.setCurrentLatitude(request.getLatitude());
        driver.setCurrentLongitude(request.getLongitude());
        Driver updated = driverRepository.save(driver);
        return DriverResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<EligibleDriverResponse> findEligibleAvailableDrivers(String area) {
        List<Driver> drivers;
        if (StringUtils.hasText(area)) {
            drivers = driverRepository.findByStatusAndAvailabilityAndServiceAreaIgnoreCase(
                    DriverStatus.ACTIVE,
                    AvailabilityStatus.AVAILABLE,
                    area.trim()
            );
        } else {
            drivers = driverRepository.findByStatusAndAvailability(
                    DriverStatus.ACTIVE,
                    AvailabilityStatus.AVAILABLE
            );
        }

        return drivers.stream()
                .map(EligibleDriverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private void validateOwnerOrAdmin(Long targetUserId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        boolean isOwner = currentUser.getUserId().equals(targetUserId);
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Access denied: You are not authorized to modify this driver profile");
        }
    }
}
