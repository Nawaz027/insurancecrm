package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.DashboardSummaryResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerRepository customerRepository;

    public DashboardSummaryResponse getSummary(String currentUserId, boolean isAdmin) {
        List<Customer> customers = isAdmin
                ? customerRepository.findAll()
                : customerRepository.findByAssignedAgentId(currentUserId);

        Map<CommunicationOutcome, Long> outcomeCounts = customers.stream()
                .map(Customer::getLastOutcome)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()));

        return DashboardSummaryResponse.builder()
                .totalCustomers(customers.size())
                .outcomeCounts(outcomeCounts)
                .build();
    }
}
