package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.AuditLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateCustomerRequest;
import com.example.insurancecrm.dto.response.BulkAssignResponse;
import com.example.insurancecrm.dto.response.CustomerResponse;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.AuditLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.AccessControl;
import com.example.insurancecrm.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    private static final String CUSTOMER_ENTITY_TYPE = "CUSTOMER";

    public List<CustomerResponse> getAllCustomers(String currentUserId, boolean isAdmin) {
        List<Customer> customers = isAdmin
                ? customerRepository.findAll()
                : customerRepository.findByAssignedAgentId(currentUserId);
        return enrichAndMap(customers);
    }

    public CustomerResponse getById(String id) {
        return enrichAndMap(List.of(findById(id))).get(0);
    }

    /** Loads a customer, enforcing that non-admins may only reach their own assigned customers. */
    public Customer findByIdForAgent(String id, String currentUserId, boolean isAdmin) {
        Customer customer = findById(id);
        AccessControl.requireOwnerOrAdmin(customer.getAssignedAgentId(), currentUserId, isAdmin);
        return customer;
    }

    public CustomerResponse getByIdForView(String id, String viewerId, String viewerName, boolean isAdmin) {
        Customer customer = findByIdForAgent(id, viewerId, isAdmin);
        CustomerResponse response = enrichAndMap(List.of(customer)).get(0);

        auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(CUSTOMER_ENTITY_TYPE, id).stream()
                .findFirst()
                .ifPresent(lastView -> {
                    response.setLastOpenedAt(lastView.getTimestamp());
                    response.setLastOpenedByName(lastView.getPerformedByName());
                });

        auditLogRepository.save(AuditLog.builder()
                .action("VIEWED")
                .entityType(CUSTOMER_ENTITY_TYPE)
                .entityId(id)
                .performedBy(viewerId)
                .performedByName(viewerName)
                .timestamp(LocalDateTime.now())
                .build());

        return response;
    }

    public CustomerResponse create(CreateCustomerRequest request) {
        Customer customer = Customer.builder()
                .name(request.getName())
                .phone(PhoneUtil.normalize(request.getPhone()))
                .email(request.getEmail())
                .address(request.getAddress())
                .notes(request.getNotes())
                .dateOfBirth(request.getDateOfBirth())
                .plan(request.getPlan())
                .lastYearPremium(request.getLastYearPremium())
                .expiryDate(request.getExpiryDate())
                .assignedAgentId(request.getAssignedAgentId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return enrichAndMap(List.of(customerRepository.save(customer))).get(0);
    }

    public CustomerResponse update(String id, CreateCustomerRequest request) {
        Customer customer = findById(id);
        customer.setName(request.getName());
        customer.setPhone(PhoneUtil.normalize(request.getPhone()));
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setNotes(request.getNotes());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setPlan(request.getPlan());
        customer.setLastYearPremium(request.getLastYearPremium());
        customer.setExpiryDate(request.getExpiryDate());
        if (request.getAssignedAgentId() != null) {
            customer.setAssignedAgentId(request.getAssignedAgentId());
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return enrichAndMap(List.of(customerRepository.save(customer))).get(0);
    }

    public CustomerResponse assignAgent(String customerId, String agentId) {
        Customer customer = findById(customerId);
        userRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound("Agent not found: " + agentId));
        customer.setAssignedAgentId(agentId);
        customer.setUpdatedAt(LocalDateTime.now());
        return enrichAndMap(List.of(customerRepository.save(customer))).get(0);
    }

    public BulkAssignResponse bulkAssignAgent(List<String> customerIds, String agentId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound("Agent not found: " + agentId));

        List<String> distinctIds = customerIds.stream().distinct().toList();
        List<Customer> found = customerRepository.findAllById(distinctIds);

        Set<String> foundIds = found.stream().map(Customer::getId).collect(Collectors.toSet());
        List<String> notFound = distinctIds.stream().filter(id -> !foundIds.contains(id)).toList();

        LocalDateTime now = LocalDateTime.now();
        found.forEach(c -> { c.setAssignedAgentId(agentId); c.setUpdatedAt(now); });
        List<Customer> saved = customerRepository.saveAll(found);

        return BulkAssignResponse.builder()
                .agentId(agentId)
                .agentName(agent.getName())
                .requestedCount(distinctIds.size())
                .assignedCount(saved.size())
                .notFoundCustomerIds(notFound)
                .customers(enrichAndMap(saved))
                .build();
    }

    public List<CustomerResponse> search(String query, String currentUserId, boolean isAdmin) {
        List<Customer> results = customerRepository.searchByNameOrPhone(query);
        if (!isAdmin) {
            results = results.stream()
                    .filter(c -> currentUserId.equals(c.getAssignedAgentId()))
                    .toList();
        }
        return enrichAndMap(results);
    }

    public void delete(String id) {
        customerRepository.delete(findById(id));
    }

    public Customer findById(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + id));
    }

    private List<CustomerResponse> enrichAndMap(List<Customer> customers) {
        List<String> agentIds = customers.stream()
                .filter(c -> c.getAssignedAgentId() != null)
                .map(Customer::getAssignedAgentId)
                .distinct().toList();

        Map<String, String> agentNames = userRepository.findAllById(agentIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return customers.stream().map(c -> CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .address(c.getAddress())
                .notes(c.getNotes())
                .dateOfBirth(c.getDateOfBirth())
                .plan(c.getPlan())
                .lastYearPremium(c.getLastYearPremium())
                .expiryDate(c.getExpiryDate())
                .assignedAgentId(c.getAssignedAgentId())
                .assignedAgentName(c.getAssignedAgentId() != null
                        ? agentNames.get(c.getAssignedAgentId()) : null)
                .lastOutcome(c.getLastOutcome())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build()).toList();
    }
}
