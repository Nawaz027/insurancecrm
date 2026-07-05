package com.example.insurancecrm.dto.request;

import com.example.insurancecrm.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLeadStatusRequest {

    @NotNull
    private LeadStatus status;

    private String lostReason;
}
