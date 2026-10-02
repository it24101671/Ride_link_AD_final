package com.ridelink.ride.controller;

import com.ridelink.ride.dto.*;
import com.ridelink.ride.security.UserPrincipal;
import com.ridelink.ride.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Ride Management", description = "Endpoints for ride creation, driver assignment, ride status lifecycle, acceptance, start, completion, cancellation, and retrieval")
@SecurityRequirement(name = "bearerAuth")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @Operation(summary = "Create ride request", description = "Creates a new ride request in REQUESTED status for the authenticated passenger.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ride request created successfully",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> createRide(
            @Valid @RequestBody RideCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        RideResponse response = rideService.createRide(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ride by ID", description = "Retrieves details of a specific ride. Accessible only by the ride passenger, assigned driver, or ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride details retrieved",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot access another user's ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> getRideById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.getRideById(id, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Get my ride history", description = "Retrieves all rides requested by the currently authenticated passenger.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of current passenger's rides",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RideResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<RideResponse>> getMyRides(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<RideResponse> responses = rideService.getMyRides(currentUser);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get driver's assigned rides", description = "Retrieves rides assigned to a specific driver. Accessible by the assigned driver or ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of driver's rides",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RideResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot view another driver's rides",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<RideResponse>> getRidesByDriver(
            @PathVariable Long driverId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        List<RideResponse> responses = rideService.getRidesByDriver(driverId, currentUser, token);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign driver to ride", description = "Assigns an eligible driver to a REQUESTED ride. Transitions status to ASSIGNED. Verifies driver with Driver Service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver successfully assigned",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ride state or driver unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride or driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Driver Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> assignDriver(
            @PathVariable Long id,
            @RequestBody(required = false) RideAssignRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.assignDriver(id, request, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/accept")
    @Operation(summary = "Accept assigned ride", description = "Driver accepts an assigned ride. Transitions status from ASSIGNED to ACCEPTED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride accepted",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ride state (must be ASSIGNED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned driver can accept",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> acceptRide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.acceptRide(id, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/start")
    @Operation(summary = "Start ride trip", description = "Driver starts the trip. Transitions status from ACCEPTED to IN_PROGRESS.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride started",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ride state (must be ACCEPTED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned driver can start",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> startRide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.startRide(id, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Complete ride trip", description = "Driver completes the trip. Transitions status from IN_PROGRESS to COMPLETED. Finalizes fare via Fare Service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride completed",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ride state (must be IN_PROGRESS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned driver can complete",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> completeRide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.completeRide(id, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel ride", description = "Cancels a ride. Enforces role-aware and state-aware rules (Passenger can cancel before acceptance/start, Driver before trip start, Admin at non-terminal states).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride cancelled",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid cancellation attempt (already completed or invalid state)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not authorized to cancel this ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideResponse> cancelRide(
            @PathVariable Long id,
            @RequestBody(required = false) RideCancelRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        RideResponse response = rideService.cancelRide(id, request, currentUser, token);
        return ResponseEntity.ok(response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
