package com.trackflow.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotBlank
    private String origin;

    @NotBlank
    private String destination;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientEmail;
}
