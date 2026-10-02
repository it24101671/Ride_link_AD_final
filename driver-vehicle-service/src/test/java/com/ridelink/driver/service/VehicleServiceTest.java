package com.ridelink.driver.service;

import com.ridelink.driver.dto.VehicleRequest;
import com.ridelink.driver.dto.VehicleResponse;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleType;
import com.ridelink.driver.exception.DriverNotFoundException;
import com.ridelink.driver.exception.DuplicateResourceException;
import com.ridelink.driver.exception.VehicleNotFoundException;
import com.ridelink.driver.repository.DriverRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private UserPrincipal driverPrincipal;
    private Driver sampleDriver;
    private Vehicle sampleVehicle;

    @BeforeEach
    void setUp() {
        driverPrincipal = new UserPrincipal(25L, "driver@example.com", "DRIVER");

        sampleDriver = new Driver();
        sampleDriver.setId(10L);
        sampleDriver.setUserId(25L);

        sampleVehicle = new Vehicle(50L, sampleDriver, "CAB-1234", VehicleType.CAR, "Toyota Prius", "White", 2022);
    }

    @Test
    @DisplayName("Should add valid vehicle to driver")
    void testAddValidVehicle() {
        VehicleRequest req = new VehicleRequest("CAB-1234", VehicleType.CAR, "Toyota Prius", "White", 2022);

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(vehicleRepository.existsByVehicleNumberIgnoreCase("CAB-1234")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(sampleVehicle);

        VehicleResponse response = vehicleService.addVehicle(10L, req, driverPrincipal);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals("CAB-1234", response.getVehicleNumber());
        assertEquals(VehicleType.CAR, response.getVehicleType());
        assertEquals("Toyota Prius", response.getModel());
    }

    @Test
    @DisplayName("Should reject duplicate vehicle number")
    void testRejectDuplicateVehicleNumber() {
        VehicleRequest req = new VehicleRequest("CAB-1234", VehicleType.CAR, "Toyota Prius", "White", 2022);

        when(driverRepository.findById(10L)).thenReturn(Optional.of(sampleDriver));
        when(vehicleRepository.existsByVehicleNumberIgnoreCase("CAB-1234")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> vehicleService.addVehicle(10L, req, driverPrincipal));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotFoundException when adding vehicle to nonexistent driver")
    void testAddVehicleNonexistentDriver() {
        VehicleRequest req = new VehicleRequest("CAB-1234", VehicleType.CAR, "Toyota Prius", "White", 2022);

        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(DriverNotFoundException.class, () -> vehicleService.addVehicle(999L, req, driverPrincipal));
    }

    @Test
    @DisplayName("Should retrieve vehicles by driver ID")
    void testGetVehiclesByDriver() {
        when(driverRepository.existsById(10L)).thenReturn(true);
        when(vehicleRepository.findByDriverId(10L)).thenReturn(List.of(sampleVehicle));

        List<VehicleResponse> vehicles = vehicleService.getVehiclesByDriver(10L);

        assertEquals(1, vehicles.size());
        assertEquals("CAB-1234", vehicles.get(0).getVehicleNumber());
    }

    @Test
    @DisplayName("Should update vehicle details")
    void testUpdateVehicle() {
        VehicleRequest req = new VehicleRequest("CAB-9999", VehicleType.SUV, "Honda Vezel", "Black", 2023);

        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(sampleVehicle));
        when(vehicleRepository.existsByVehicleNumberIgnoreCase("CAB-9999")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(sampleVehicle);

        VehicleResponse response = vehicleService.updateVehicle(50L, req, driverPrincipal);

        assertNotNull(response);
        assertEquals("CAB-9999", sampleVehicle.getVehicleNumber());
        assertEquals(VehicleType.SUV, sampleVehicle.getVehicleType());
        assertEquals("Black", sampleVehicle.getColor());
    }

    @Test
    @DisplayName("Should delete vehicle by ID")
    void testDeleteVehicle() {
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(sampleVehicle));

        vehicleService.deleteVehicle(50L, driverPrincipal);

        verify(vehicleRepository, times(1)).delete(sampleVehicle);
    }

    @Test
    @DisplayName("Should throw VehicleNotFoundException when vehicle not found")
    void testVehicleNotFound() {
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> vehicleService.getVehicleById(999L));
    }
}
