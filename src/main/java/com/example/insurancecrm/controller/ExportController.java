package com.example.insurancecrm.controller;

import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Data Export", description = "Download customers as Excel (.xlsx) files.")
public class ExportController {

    private final ExportService exportService;
    private final UserRepository userRepository;

    @Operation(summary = "Export customers to Excel",
               description = "Downloads an .xlsx file with contact details, plan/premium/expiry info, and assigned agent. " +
                             "Agents always get only their own customers. Admins get everyone by default, or a single " +
                             "agent's customers via the optional agentId filter.")
    @GetMapping("/customers")
    public ResponseEntity<byte[]> exportCustomers(
            @Parameter(description = "Admin-only filter — MongoDB ID of the agent to export customers for") @RequestParam(required = false) String agentId,
            Authentication auth) throws Exception {
        byte[] data = exportService.exportCustomers(agentId, getUserId(auth), isAdmin(auth));
        return xlsxResponse(data, "customers_" + LocalDate.now() + ".xlsx");
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}
