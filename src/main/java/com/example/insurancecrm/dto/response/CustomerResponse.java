package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String notes;
    private LocalDate dateOfBirth;
    private String plan;
    private BigDecimal lastYearPremium;
    private LocalDate expiryDate;
    private String assignedAgentId;
    private String assignedAgentName;
    private CommunicationOutcome lastOutcome;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastOpenedAt;
    private String lastOpenedByName;
}
