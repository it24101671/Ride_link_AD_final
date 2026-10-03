package com.ridelink.payment.controller;

import com.ridelink.payment.dto.ErrorResponse;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.security.UserPrincipal;
import com.ridelink.payment.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipts")
@Tag(name = "Receipt Management", description = "Endpoints for retrieving itemized payment receipts")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping({"/{paymentId}", "/payment/{paymentId}"})
    @Operation(summary = "Get receipt by payment ID", description = "Retrieves an itemized receipt for a completed payment. Accessible by ride passenger, driver, or ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt generated successfully",
                    content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payment not in completed/successful state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot view another user's receipt",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReceiptResponse> getReceiptByPaymentId(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        ReceiptResponse response = receiptService.getReceiptByPaymentId(paymentId, currentUser, token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ride/{rideId}")
    @Operation(summary = "Get receipt by ride ID", description = "Retrieves an itemized receipt for a completed ride. Accessible by ride passenger, driver, or ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt generated successfully",
                    content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ride payment not successful",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Receipt not found for ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReceiptResponse> getReceiptByRideId(
            @PathVariable Long rideId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        ReceiptResponse response = receiptService.getReceiptByRideId(rideId, currentUser, token);
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
