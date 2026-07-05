package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkAssignResponse {
    private String agentId;
    private String agentName;
    private int requestedCount;
    private int assignedCount;
    private List<String> notFoundCustomerIds;
    private List<CustomerResponse> customers;
}
