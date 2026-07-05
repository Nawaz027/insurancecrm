package com.example.insurancecrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkAssignRequest {

    @NotEmpty(message = "customerIds must not be empty")
    private List<String> customerIds;

    @NotBlank
    private String agentId;
}
