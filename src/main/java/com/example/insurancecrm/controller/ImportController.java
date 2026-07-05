package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.ImportResultResponse;
import com.example.insurancecrm.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Data Import", description = "Admin-only. Bulk import customers via Excel (.xlsx) or CSV files.")
public class ImportController {

    private final ImportService importService;

    @Operation(
        summary = "Import customers from Excel/CSV",
        description = """
            Upload an .xlsx or .csv file to bulk-create or update customers (upsert on phone number).

            **Required columns (row 1 = header, row 2+ = data):**
            | Column | Field | Required |
            |--------|-------|----------|
            | A | Name | Yes |
            | B | Plan | No |
            | C | Last Year Premium | No (numeric) |
            | D | Expiry Date | No (YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY, or MM/DD/YYYY) |
            | E | Email | No |
            | F | DOB | No (same date formats as Expiry Date) |
            | G | Phone | Yes |
            | H | Address | No |
            | I | Notes | No |

            Dates and the premium amount are parsed best-effort — an unparseable value is left blank rather than
            failing the row, since only Name and Phone are required for a usable customer record.
            Agent assignment is done after import via the Customers page.
            Returns a result with success count, failure count, and per-row error details.
            """
    )
    @PostMapping(value = "/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultResponse>> importCustomers(
            @RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.ok("File is empty",
                    ImportResultResponse.builder().totalRows(0).successCount(0).failureCount(0).errors(java.util.List.of()).build()));
        }
        ImportResultResponse result = importService.importCustomers(file);
        String message = result.getCreatedCount() + " created, " + result.getUpdatedCount() + " updated"
                + (result.getFailureCount() > 0 ? ", " + result.getFailureCount() + " failed" : "");
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }
}
