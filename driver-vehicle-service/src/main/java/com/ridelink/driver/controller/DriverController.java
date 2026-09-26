package com.ridelink.driver.controller;

import com.ridelink.driver.dto.*;
import com.ridelink.driver.security.UserPrincipal;
import com.ridelink.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Driver Management", description = "Endpoints for driver operational profiles, availability, location, and eligible driver queries")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create driver profile", description = "Creates an operational driver profile with license number and service area")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Driver profile created",
                    content = @Content(schema = @Schema(implementation = DriverResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate user or license number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DriverResponse> createDriver(
            @Valid @RequestBody DriverCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        DriverResponse response = driverService.createDriver(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get driver by ID", description = "Retrieves operational details of a driver")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver profile found",
                    content = @Content(schema = @Schema(implementation = DriverResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DriverResponse> getDriverById(@PathVariable Long id) {
        DriverResponse response = driverService.getDriverById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get driver by Account userId", description = "Retrieves operational driver profile using the cross-service userId")
    public ResponseEntity<DriverResponse> getDriverByUserId(@PathVariable Long userId) {
        DriverResponse response = driverService.getDriverByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update driver profile", description = "Updates driver license number and service area")
    public ResponseEntity<DriverResponse> updateDriver(
            @PathVariable Long id,
            @Valid @RequestBody DriverUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        DriverResponse response = driverService.updateDriver(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/availability")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update driver availability", description = "Toggles driver availability between AVAILABLE and UNAVAILABLE")
    public ResponseEntity<DriverResponse> updateAvailability(
            @PathVariable Long id,
            @Valid @RequestBody AvailabilityUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        DriverResponse response = driverService.updateAvailability(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/location")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update simulated location", description = "Updates simulated GPS coordinates (latitude between -90 and 90, longitude between -180 and 180)")
    public ResponseEntity<DriverResponse> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        DriverResponse response = driverService.updateLocation(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    @Operation(summary = "Find eligible available drivers", description = "Returns active, available drivers, optionally filtered by service area for Ride Management")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of eligible available drivers",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EligibleDriverResponse.class))))
    })
    public ResponseEntity<List<EligibleDriverResponse>> findEligibleAvailableDrivers(
            @RequestParam(required = false) String area
    ) {
        List<EligibleDriverResponse> response = driverService.findEligibleAvailableDrivers(area);
        return ResponseEntity.ok(response);
    }
}
