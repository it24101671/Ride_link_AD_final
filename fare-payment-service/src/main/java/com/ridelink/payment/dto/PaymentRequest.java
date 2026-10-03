package com.ridelink.payment.dto;

import com.ridelink.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PaymentRequest {

    @NotNull(message = "Ride ID is required")
    private Long rideId;

    @NotNull(message = "Payment method is required (CARD, CASH, WALLET)")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    private String paymentToken;

    private Boolean simulateFailure = false;

    public PaymentRequest() {
    }

    public PaymentRequest(Long rideId, PaymentMethod paymentMethod) {
        this.rideId = rideId;
        this.paymentMethod = paymentMethod;
    }

    public PaymentRequest(Long rideId, PaymentMethod paymentMethod, BigDecimal amount) {
        this.rideId = rideId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
    }

    public PaymentRequest(Long rideId, PaymentMethod paymentMethod, BigDecimal amount, String paymentToken, Boolean simulateFailure) {
        this.rideId = rideId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.paymentToken = paymentToken;
        this.simulateFailure = simulateFailure;
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    public Boolean getSimulateFailure() {
        return simulateFailure;
    }

    public void setSimulateFailure(Boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
