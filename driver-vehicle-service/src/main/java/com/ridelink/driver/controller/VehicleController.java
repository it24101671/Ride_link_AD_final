package com.ridelink.driver.controller;

import com.ridelink.driver.dto.ErrorResponse;
import com.ridelink.driver.dto.VehicleRequest;
import com.ridelink.driver.dto.VehicleResponse;
import com.ridelink.driver.security.UserPrincipal;
import com.ridelink.driver.service.VehicleService;
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
@RequestMapping("/api")
@Tag(name = "Vehicle Management", description = "Endpoints for driver vehicles and lifecycle operations")
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping("/drivers/{driverId}/vehicles")
    @Operation(summary = "Add vehicle to driver", description = "Registers a new vehicle associated with the driver")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vehicle registered",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate vehicle number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> addVehicle(
            @PathVariable Long driverId,
            @Valid @RequestBody VehicleRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        VehicleResponse response = vehicleService.addVehicle(driverId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/drivers/{driverId}/vehicles")
    @Operation(summary = "List vehicles of a driver", description = "Retrieves all vehicles belonging to the specified driver")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of vehicles",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = VehicleResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getVehiclesByDriver(@PathVariable Long driverId) {
        List<VehicleResponse> response = vehicleService.getVehiclesByDriver(driverId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicles/{vehicleId}")
    @Operation(summary = "Get vehicle by ID", description = "Retrieves vehicle information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle found",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable Long vehicleId) {
        VehicleResponse response = vehicleService.getVehicleById(vehicleId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/vehicles/{vehicleId}")
    @Operation(summary = "Update vehicle", description = "Updates details of an existing vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle updated",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate vehicle number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable Long vehicleId,
            @Valid @RequestBody VehicleRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        VehicleResponse response = vehicleService.updateVehicle(vehicleId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/vehicles/{vehicleId}")
    @Operation(summary = "Delete vehicle", description = "Deletes a vehicle by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Vehicle successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteVehicle(
            @PathVariable Long vehicleId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        vehicleService.deleteVehicle(vehicleId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
