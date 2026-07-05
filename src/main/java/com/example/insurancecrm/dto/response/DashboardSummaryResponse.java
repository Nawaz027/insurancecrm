package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DashboardSummaryResponse {
    private long totalCustomers;
    private Map<CommunicationOutcome, Long> outcomeCounts;
}
