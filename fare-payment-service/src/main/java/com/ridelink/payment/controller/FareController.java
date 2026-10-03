package com.ridelink.payment.controller;

import com.ridelink.payment.dto.ErrorResponse;
import com.ridelink.payment.dto.FareCalculationRequest;
import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareResponse;
import com.ridelink.payment.service.FareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Fare Management", description = "Endpoints for fare estimation, final fare calculation, and fare retrieval")
public class FareController {

    private final FareService fareService;

    public FareController(FareService fareService) {
        this.fareService = fareService;
    }

    @PostMapping({"/api/fares/estimate", "/api/fare/estimate"})
    @Operation(summary = "Estimate fare", description = "Calculates and returns estimated fare based on pickup and destination locations.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare estimated successfully",
                    content = @Content(schema = @Schema(implementation = FareResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FareResponse> estimateFare(@Valid @RequestBody FareEstimateRequest request) {
        FareResponse response = fareService.estimateFare(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/api/fares/calculate", "/api/fare/final"})
    @Operation(summary = "Calculate final fare", description = "Calculates, finalizes, and persists the final fare for a completed ride.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Final fare calculated successfully",
                    content = @Content(schema = @Schema(implementation = FareResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid ride data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FareResponse> calculateFinalFare(
            @Valid @RequestBody FareCalculationRequest request,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        FareResponse response = fareService.calculateFinalFare(request, token);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/fares/{id}", "/api/fare/{id}"})
    @Operation(summary = "Get fare by ID", description = "Retrieves stored fare calculation details by fare ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare details retrieved",
                    content = @Content(schema = @Schema(implementation = FareResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fare not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FareResponse> getFareById(@PathVariable Long id) {
        FareResponse response = fareService.getFareById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/fares/ride/{rideId}", "/api/fare/ride/{rideId}"})
    @Operation(summary = "Get fare by ride ID", description = "Retrieves the most recent fare calculation associated with a specific ride ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare details retrieved",
                    content = @Content(schema = @Schema(implementation = FareResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fare not found for ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FareResponse> getFareByRideId(@PathVariable Long rideId) {
        FareResponse response = fareService.getFareByRideId(rideId);
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
