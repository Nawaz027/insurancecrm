package com.example.insurancecrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCustomerRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    private String email;

    private String address;

    private String notes;

    private LocalDate dateOfBirth;

    private String plan;

    private BigDecimal lastYearPremium;

    private LocalDate expiryDate;

    private String assignedAgentId;
}
