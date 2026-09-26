package com.ridelink.driver.service;

import com.ridelink.driver.dto.*;
import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.DriverStatus;
import com.ridelink.driver.exception.DriverNotFoundException;
import com.ridelink.driver.exception.DuplicateResourceException;
import com.ridelink.driver.repository.DriverRepository;
import com.ridelink.driver.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverService driverService;

    private UserPrincipal driverPrincipal;
    private Driver sampleDriver;

    @BeforeEach
    void setUp() {
        driverPrincipal = new UserPrincipal(25L, "driver@example.com", "DRIVER");

        sampleDriver = new Driver();
        sampleDriver.setId(10L);
        sampleDriver.setUserId(25L);
        sampleDriver.setLicenseNumber("B1234567");
        sampleDriver.setServiceArea("Colombo");
        sampleDriver.setAvailability(AvailabilityStatus.UNAVAILABLE);
        sampleDriver.setStatus(DriverStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should create valid operational driver profile")
    void testCreateValidDriver() {
        DriverCreateRequest request = new DriverCreateRequest(25L, "B1234567", "Colombo");

        when(driverRepository.existsByUserId(25L)).thenReturn(false);
        when(driverRepository.existsByLicenseNumberIgnoreCase("B1234567")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> {
            Driver d = inv.getArgument(0);
            d.setId(10L);
            return d;
        });

        DriverResponse response = driverService.createDriver(request, driverPrincipal);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(25L, response.getUserId());
        assertEquals("B1234567", response.getLicenseNumber());
        assertEquals("Colombo", response.getServiceArea());
        assertEquals(AvailabilityStatus.UNAVAILABLE, response.getAvailability());
        assertEquals(DriverStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("Should reject duplicate license number with DuplicateResourceException")
    void testRejectDuplicateLicense() {
        DriverCreateRequest request = new DriverCreateRequest(25L, "B1234567", "Colombo");

        when(driverRepository.existsByUserId(25L)).thenReturn(false);
        when(driverRepository.existsByLicenseNumberIgnoreCase("B1234567")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(request, driverPrincipal));
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update driver profile")
    void testUpdateDriverProfile() {
        DriverUpdateRequest updateReq = new DriverUpdateRequest("B7654321", "Kandy");

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.existsByLicenseNumberIgnoreCase("B7654321")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponse response = driverService.updateDriver(10L, updateReq, driverPrincipal);

        assertNotNull(response);
        assertEquals("B7654321", sampleDriver.getLicenseNumber());
        assertEquals("Kandy", sampleDriver.getServiceArea());
    }

    @Test
    @DisplayName("Should set driver availability to AVAILABLE")
    void testSetAvailable() {
        AvailabilityUpdateRequest req = new AvailabilityUpdateRequest(AvailabilityStatus.AVAILABLE);

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponse response = driverService.updateAvailability(10L, req, driverPrincipal);

        assertNotNull(response);
        assertEquals(AvailabilityStatus.AVAILABLE, sampleDriver.getAvailability());
    }

    @Test
    @DisplayName("Should set driver availability to UNAVAILABLE")
    void testSetUnavailable() {
        sampleDriver.setAvailability(AvailabilityStatus.AVAILABLE);
        AvailabilityUpdateRequest req = new AvailabilityUpdateRequest(AvailabilityStatus.UNAVAILABLE);

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponse response = driverService.updateAvailability(10L, req, driverPrincipal);

        assertNotNull(response);
        assertEquals(AvailabilityStatus.UNAVAILABLE, sampleDriver.getAvailability());
    }

    @Test
    @DisplayName("Should update simulated GPS coordinates")
    void testUpdateLocation() {
        LocationUpdateRequest req = new LocationUpdateRequest(new BigDecimal("6.9271"), new BigDecimal("79.8612"));

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponse response = driverService.updateLocation(10L, req, driverPrincipal);

        assertNotNull(response);
        assertEquals(new BigDecimal("6.9271"), sampleDriver.getCurrentLatitude());
        assertEquals(new BigDecimal("79.8612"), sampleDriver.getCurrentLongitude());
    }

    @Test
    @DisplayName("Should return only ACTIVE + AVAILABLE drivers, filtered by area")
    void testFilterAvailableDriversByArea() {
        sampleDriver.setAvailability(AvailabilityStatus.AVAILABLE);
        sampleDriver.setCurrentLatitude(new BigDecimal("6.9271"));
        sampleDriver.setCurrentLongitude(new BigDecimal("79.8612"));

        when(driverRepository.findByStatusAndAvailabilityAndServiceAreaIgnoreCase(
                DriverStatus.ACTIVE, AvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(sampleDriver));

        List<EligibleDriverResponse> list = driverService.findEligibleAvailableDrivers("Colombo");

        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getDriverId());
        assertEquals(25L, list.get(0).getUserId());
        assertEquals("Colombo", list.get(0).getServiceArea());
        assertEquals(AvailabilityStatus.AVAILABLE, list.get(0).getAvailability());
    }

    @Test
    @DisplayName("Should throw DriverNotFoundException for unknown driver")
    void testDriverNotFound() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(DriverNotFoundException.class, () -> driverService.getDriverById(999L));
    }
}
