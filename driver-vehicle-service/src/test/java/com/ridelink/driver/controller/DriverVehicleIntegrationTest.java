package com.ridelink.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.driver.dto.AvailabilityUpdateRequest;
import com.ridelink.driver.dto.DriverCreateRequest;
import com.ridelink.driver.dto.LocationUpdateRequest;
import com.ridelink.driver.dto.VehicleRequest;
import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.VehicleType;
import com.ridelink.driver.repository.DriverRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DriverVehicleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JwtService jwtService;

    private String driverToken;
    private String adminToken;
    private final Long testUserId = 55L;

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();

        driverToken = jwtService.generateToken(testUserId, "driver55@example.com", "DRIVER");
        adminToken = jwtService.generateToken(999L, "admin@ridelink.com", "ADMIN");
    }

    @Test
    @DisplayName("POST /api/drivers - Successfully create driver profile")
    void testCreateDriverSuccess() throws Exception {
        DriverCreateRequest request = new DriverCreateRequest(testUserId, "LIC-9988", "Colombo");

        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(testUserId.intValue())))
                .andExpect(jsonPath("$.licenseNumber", is("LIC-9988")))
                .andExpect(jsonPath("$.serviceArea", is("Colombo")))
                .andExpect(jsonPath("$.availability", is("UNAVAILABLE")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("POST /api/drivers - Reject duplicate license with 409 Conflict")
    void testCreateDriverDuplicateLicense() throws Exception {
        Driver driver = new Driver(null, 100L, "DUP-LIC", AvailabilityStatus.UNAVAILABLE, "Colombo", null, null, null);
        driverRepository.save(driver);

        DriverCreateRequest request = new DriverCreateRequest(testUserId, "DUP-LIC", "Kandy");

        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/location - Reject invalid coordinates with 400")
    void testRejectInvalidCoordinates() throws Exception {
        Driver driver = driverRepository.save(new Driver(null, testUserId, "LIC-COORD", AvailabilityStatus.UNAVAILABLE, "Colombo", null, null, null));

        // Latitude > 90
        LocationUpdateRequest invalidLat = new LocationUpdateRequest(new BigDecimal("95.0000"), new BigDecimal("79.8612"));

        mockMvc.perform(patch("/api/drivers/" + driver.getId() + "/location")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Driver Availability & Location Update Workflow, and Eligible Driver Query")
    void testAvailabilityLocationAndEligibleDriverWorkflow() throws Exception {
        Driver driver = driverRepository.save(new Driver(null, testUserId, "LIC-FLOW", AvailabilityStatus.UNAVAILABLE, "Colombo", null, null, null));

        // 1. Update location
        LocationUpdateRequest locReq = new LocationUpdateRequest(new BigDecimal("6.9271"), new BigDecimal("79.8612"));
        mockMvc.perform(patch("/api/drivers/" + driver.getId() + "/location")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLatitude", is(6.9271)))
                .andExpect(jsonPath("$.currentLongitude", is(79.8612)));

        // 2. Before setting AVAILABLE, query should return 0 eligible drivers
        mockMvc.perform(get("/api/drivers/available?area=Colombo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 3. Set driver AVAILABLE
        AvailabilityUpdateRequest availReq = new AvailabilityUpdateRequest(AvailabilityStatus.AVAILABLE);
        mockMvc.perform(patch("/api/drivers/" + driver.getId() + "/availability")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(availReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is("AVAILABLE")));

        // 4. Query eligible drivers in 'Colombo' -> should return 1 driver
        mockMvc.perform(get("/api/drivers/available?area=Colombo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(driver.getId().intValue())))
                .andExpect(jsonPath("$[0].userId", is(testUserId.intValue())))
                .andExpect(jsonPath("$[0].serviceArea", is("Colombo")))
                .andExpect(jsonPath("$[0].availability", is("AVAILABLE")))
                .andExpect(jsonPath("$[0].currentLatitude", is(6.9271)))
                .andExpect(jsonPath("$[0].currentLongitude", is(79.8612)));

        // 5. Query eligible drivers in 'Galle' -> should return 0
        mockMvc.perform(get("/api/drivers/available?area=Galle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Vehicle Lifecycle: Add vehicle, reject duplicate, update, and delete")
    void testVehicleLifecycle() throws Exception {
        Driver driver = driverRepository.save(new Driver(null, testUserId, "LIC-VEH", AvailabilityStatus.UNAVAILABLE, "Colombo", null, null, null));

        // 1. Add vehicle
        VehicleRequest vehReq = new VehicleRequest("CAB-7788", VehicleType.CAR, "Honda Fit", "White", 2021);
        String createVehResp = mockMvc.perform(post("/api/drivers/" + driver.getId() + "/vehicles")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleNumber", is("CAB-7788")))
                .andExpect(jsonPath("$.model", is("Honda Fit")))
                .andReturn().getResponse().getContentAsString();

        Long vehicleId = objectMapper.readTree(createVehResp).get("id").asLong();

        // 2. Reject duplicate vehicle number
        mockMvc.perform(post("/api/drivers/" + driver.getId() + "/vehicles")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));

        // 3. Update vehicle
        VehicleRequest updateReq = new VehicleRequest("CAB-7788", VehicleType.CAR, "Honda Grace", "Silver", 2022);
        mockMvc.perform(put("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model", is("Honda Grace")))
                .andExpect(jsonPath("$.color", is("Silver")));

        // 4. Delete vehicle
        mockMvc.perform(delete("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        // 5. Verify vehicle is deleted
        mockMvc.perform(get("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNotFound());
    }
}
