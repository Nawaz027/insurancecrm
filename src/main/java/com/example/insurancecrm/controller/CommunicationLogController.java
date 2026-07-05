package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.request.CreateCommunicationLogRequest;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.CommunicationLogResponse;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.CommunicationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Communication Log", description = "Log and retrieve interaction history for customers and leads.")
public class CommunicationLogController {

    private final CommunicationLogService logService;
    private final UserRepository userRepository;

    // ── Customer communications ───────────────────────────────────

    @Operation(summary = "Get communication history for a customer",
               description = "Returns all logged interactions for this customer, newest first.")
    @GetMapping("/api/customers/{customerId}/communications")
    public ResponseEntity<ApiResponse<List<CommunicationLogResponse>>> getByCustomer(
            @PathVariable String customerId) {
        return ResponseEntity.ok(ApiResponse.ok(logService.getByCustomer(customerId)));
    }

    @Operation(summary = "Log a communication for a customer",
               description = "Records an interaction (call, WhatsApp, email, meeting, site visit) against a customer.")
    @PostMapping("/api/customers/{customerId}/communications")
    public ResponseEntity<ApiResponse<CommunicationLogResponse>> logForCustomer(
            @PathVariable String customerId,
            @Valid @RequestBody CreateCommunicationLogRequest request,
            Authentication auth) {
        String userId = getUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(logService.logForCustomer(customerId, request, userId)));
    }

    // ── Lead communications ───────────────────────────────────────

    @Operation(summary = "Get communication history for a lead")
    @GetMapping("/api/leads/{leadId}/communications")
    public ResponseEntity<ApiResponse<List<CommunicationLogResponse>>> getByLead(
            @PathVariable String leadId) {
        return ResponseEntity.ok(ApiResponse.ok(logService.getByLead(leadId)));
    }

    @Operation(summary = "Log a communication for a lead",
               description = "Records an interaction against a lead. Useful for tracking outreach during the sales pipeline.")
    @PostMapping("/api/leads/{leadId}/communications")
    public ResponseEntity<ApiResponse<CommunicationLogResponse>> logForLead(
            @PathVariable String leadId,
            @Valid @RequestBody CreateCommunicationLogRequest request,
            Authentication auth) {
        String userId = getUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(logService.logForLead(leadId, request, userId)));
    }

    // ── Delete ────────────────────────────────────────────────────

    @Operation(summary = "Delete a communication log entry",
               description = "Agents can delete only their own entries. Admins can delete any.")
    @DeleteMapping("/api/communications/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id, Authentication auth) {
        String userId = getUserId(auth);
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        logService.delete(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.noContent("Log deleted"));
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .map(u -> u.getId()).orElse(null);
    }
}
