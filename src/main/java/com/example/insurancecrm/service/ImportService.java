package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.ImportResultResponse;
import com.example.insurancecrm.util.PhoneUtil;
import com.example.insurancecrm.dto.response.ImportResultResponse.RowError;
import com.example.insurancecrm.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final CustomerRepository customerRepository;

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
    };

    // ─────────────────────────────────────────────────────────────
    //  Customer import — upsert on phone number
    //  Columns: Name | Plan | Last Year Premium | Expiry Date | Email |
    //           DOB | Phone | Address (optional) | Notes (optional)
    // ─────────────────────────────────────────────────────────────
    public ImportResultResponse importCustomers(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        List<String[]> rows = filename.endsWith(".csv")
                ? parseCsv(file) : parseExcel(file);

        List<RowError> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            String[] cols = rows.get(i);
            String rawData = String.join(", ", cols);
            try {
                String name  = col(cols, 0);
                String phone = col(cols, 6);

                if (name.isBlank())  { errors.add(err(rowNum, rawData, "Name is required")); continue; }
                if (phone.isBlank()) { errors.add(err(rowNum, rawData, "Phone is required")); continue; }

                String normalizedPhone = PhoneUtil.normalize(phone);
                if (normalizedPhone.isBlank()) { errors.add(err(rowNum, rawData, "Phone is invalid: " + phone)); continue; }

                String plan          = col(cols, 1).isBlank() ? null : col(cols, 1);
                BigDecimal premium   = parseDecimal(col(cols, 2));
                LocalDate expiryDate = parseDate(col(cols, 3));
                String email         = col(cols, 4).isBlank() ? null : col(cols, 4);
                LocalDate dob        = parseDate(col(cols, 5));
                String address       = col(cols, 7).isBlank() ? null : col(cols, 7);
                String notes         = col(cols, 8).isBlank() ? null : col(cols, 8);

                // Upsert: find existing customer by normalized phone
                List<Customer> existing = customerRepository.searchByNameOrPhone(normalizedPhone);
                Customer match = existing.stream()
                        .filter(c -> c.getPhone().equals(normalizedPhone))
                        .findFirst().orElse(null);

                if (match != null) {
                    match.setName(name);
                    if (email       != null) match.setEmail(email);
                    if (address     != null) match.setAddress(address);
                    if (notes       != null) match.setNotes(notes);
                    if (plan        != null) match.setPlan(plan);
                    if (premium     != null) match.setLastYearPremium(premium);
                    if (expiryDate  != null) match.setExpiryDate(expiryDate);
                    if (dob         != null) match.setDateOfBirth(dob);
                    match.setUpdatedAt(LocalDateTime.now());
                    customerRepository.save(match);
                    updated++;
                } else {
                    customerRepository.save(Customer.builder()
                            .name(name).phone(normalizedPhone).email(email)
                            .address(address).notes(notes)
                            .plan(plan).lastYearPremium(premium)
                            .expiryDate(expiryDate).dateOfBirth(dob)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    created++;
                }
            } catch (Exception e) {
                errors.add(err(rowNum, rawData, "Unexpected error: " + e.getMessage()));
            }
        }

        // Encode created/updated counts into the message via the totalRows/successCount fields
        int success = created + updated;
        return ImportResultResponse.builder()
                .totalRows(rows.size())
                .successCount(success)
                .failureCount(errors.size())
                .createdCount(created)
                .updatedCount(updated)
                .errors(errors)
                .build();
    }

    // ─── Parsers ───────────────────────────────────────────────────

    private List<String[]> parseExcel(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {  // skip header row 0
                Row row = sheet.getRow(r);
                if (row == null) continue;
                boolean hasData = false;
                String[] cols = new String[row.getLastCellNum()];
                for (int c = 0; c < cols.length; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cols[c] = fmt.formatCellValue(cell).trim();
                    if (!cols[c].isBlank()) hasData = true;
                }
                if (hasData) rows.add(cols);
            }
        }
        return rows;
    }

    private List<String[]> parseCsv(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                String[] cols = new String[record.size()];
                for (int i = 0; i < record.size(); i++) cols[i] = record.get(i);
                rows.add(cols);
            }
        }
        return rows;
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private String col(String[] cols, int idx) {
        return (idx < cols.length && cols[idx] != null) ? cols[idx].trim() : "";
    }

    /** Best-effort date parsing across the common formats seen in real spreadsheets. Returns null rather than
     *  failing the row — DOB and expiry date are supplementary, not required for a customer record to be useful. */
    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s.trim(), fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /** Best-effort numeric parsing, tolerant of currency symbols/commas. Returns null rather than failing the row. */
    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RowError err(int row, String data, String message) {
        return RowError.builder().row(row).data(data).message(message).build();
    }
}
