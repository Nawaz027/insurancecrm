package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateLeadRequest;
import com.example.insurancecrm.dto.request.UpdateLeadStatusRequest;
import com.example.insurancecrm.dto.response.LeadResponse;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.AccessControl;
import com.example.insurancecrm.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public List<LeadResponse> getAll(String currentUserId, boolean isAdmin) {
        List<Lead> leads = isAdmin
                ? leadRepository.findAll()
                : leadRepository.findByAssignedAgentId(currentUserId);
        return enrich(leads);
    }

    public LeadResponse getById(String id, String currentUserId, boolean isAdmin) {
        Lead lead = findById(id);
        AccessControl.requireOwnerOrAdmin(lead.getAssignedAgentId(), currentUserId, isAdmin);
        return enrich(List.of(lead)).get(0);
    }

    public LeadResponse create(CreateLeadRequest req, String createdBy, boolean isAdmin) {
        // Only admins may assign to another agent; an agent's own leads are assigned to themselves.
        String assignedAgentId = isAdmin ? req.getAssignedAgentId() : createdBy;

        Lead lead = Lead.builder()
                .name(req.getName())
                .phone(PhoneUtil.normalize(req.getPhone()))
                .email(req.getEmail())
                .address(req.getAddress())
                .source(req.getSource())
                .status(LeadStatus.NEW)
                .interestedIn(req.getInterestedIn())
                .notes(req.getNotes())
                .estimatedPremium(req.getEstimatedPremium())
                .assignedAgentId(assignedAgentId)
                .followUpDate(req.getFollowUpDate())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return enrich(List.of(leadRepository.save(lead))).get(0);
    }

    public LeadResponse update(String id, CreateLeadRequest req, String currentUserId, boolean isAdmin) {
        Lead lead = findById(id);
        AccessControl.requireOwnerOrAdmin(lead.getAssignedAgentId(), currentUserId, isAdmin);
        lead.setName(req.getName());
        lead.setPhone(PhoneUtil.normalize(req.getPhone()));
        lead.setEmail(req.getEmail());
        lead.setAddress(req.getAddress());
        lead.setSource(req.getSource());
        lead.setInterestedIn(req.getInterestedIn());
        lead.setNotes(req.getNotes());
        lead.setEstimatedPremium(req.getEstimatedPremium());
        lead.setFollowUpDate(req.getFollowUpDate());
        // Re-assignment is an admin-only action; agents cannot hand their leads to another agent.
        if (isAdmin && req.getAssignedAgentId() != null) lead.setAssignedAgentId(req.getAssignedAgentId());
        lead.setUpdatedAt(LocalDateTime.now());
        return enrich(List.of(leadRepository.save(lead))).get(0);
    }

    public LeadResponse updateStatus(String id, UpdateLeadStatusRequest req, String currentUserId, boolean isAdmin) {
        Lead lead = findById(id);
        AccessControl.requireOwnerOrAdmin(lead.getAssignedAgentId(), currentUserId, isAdmin);
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw ApiException.badRequest("Cannot change status of a converted lead");
        }
        lead.setStatus(req.getStatus());
        if (req.getStatus() == LeadStatus.CONTACTED) lead.setLastContactedAt(LocalDateTime.now());
        if (req.getStatus() == LeadStatus.LOST && req.getLostReason() != null) {
            lead.setLostReason(req.getLostReason());
        }
        lead.setUpdatedAt(LocalDateTime.now());
        return enrich(List.of(leadRepository.save(lead))).get(0);
    }

    public LeadResponse convertToCustomer(String id, String currentUserId, boolean isAdmin) {
        Lead lead = findById(id);
        AccessControl.requireOwnerOrAdmin(lead.getAssignedAgentId(), currentUserId, isAdmin);
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw ApiException.badRequest("Lead already converted to customer");
        }

        Customer customer = Customer.builder()
                .name(lead.getName())
                .phone(lead.getPhone())
                .email(lead.getEmail())
                .address(lead.getAddress())
                .notes("Converted from lead. Source: " + (lead.getSource() != null ? lead.getSource().name() : "Unknown")
                        + (lead.getNotes() != null ? ". " + lead.getNotes() : ""))
                .assignedAgentId(lead.getAssignedAgentId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        customer = customerRepository.save(customer);

        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedCustomerId(customer.getId());
        lead.setConvertedAt(LocalDateTime.now());
        lead.setUpdatedAt(LocalDateTime.now());
        leadRepository.save(lead);

        return enrich(List.of(lead)).get(0);
    }

    public void delete(String id) {
        leadRepository.delete(findById(id));
    }

    public Lead findById(String id) {
        return leadRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lead not found: " + id));
    }

    private List<LeadResponse> enrich(List<Lead> leads) {
        List<String> userIds = leads.stream()
                .filter(l -> l.getAssignedAgentId() != null || l.getCreatedBy() != null)
                .flatMap(l -> java.util.stream.Stream.of(l.getAssignedAgentId(), l.getCreatedBy()))
                .filter(java.util.Objects::nonNull)
                .distinct().toList();

        Map<String, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return leads.stream().map(l -> LeadResponse.builder()
                .id(l.getId())
                .name(l.getName())
                .phone(l.getPhone())
                .email(l.getEmail())
                .address(l.getAddress())
                .source(l.getSource())
                .status(l.getStatus())
                .interestedIn(l.getInterestedIn())
                .notes(l.getNotes())
                .estimatedPremium(l.getEstimatedPremium())
                .assignedAgentId(l.getAssignedAgentId())
                .assignedAgentName(l.getAssignedAgentId() != null ? names.get(l.getAssignedAgentId()) : null)
                .followUpDate(l.getFollowUpDate())
                .lastContactedAt(l.getLastContactedAt())
                .lastOutcome(l.getLastOutcome())
                .convertedCustomerId(l.getConvertedCustomerId())
                .convertedAt(l.getConvertedAt())
                .lostReason(l.getLostReason())
                .createdBy(l.getCreatedBy())
                .createdByName(l.getCreatedBy() != null ? names.get(l.getCreatedBy()) : null)
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build()).toList();
    }
}
