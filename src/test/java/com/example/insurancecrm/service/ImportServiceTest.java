package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.ImportResultResponse;
import com.example.insurancecrm.repository.CustomerRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock private CustomerRepository customerRepository;

    @InjectMocks
    private ImportService importService;

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "customers.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    // Columns are read positionally: Name, Plan, Premium, Expiry, Email, DOB, Phone, Address, Notes.
    // Header labels below are deliberately real-world/mismatched (as in the user's actual export)
    // to lock in that parsing is index-based, not header-name based.
    private static final String HEADER = "Customer Name,Plan,Last Year Premium,Expiry Date,Email Address,DOB,Registered Mobile Number\n";

    @Test
    void importCustomers_newPhone_createsCustomer() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,9876543210\n"));

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getFailureCount()).isZero();

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Ajit Kumar");
        assertThat(captor.getValue().getPhone()).isEqualTo("9876543210");
        assertThat(captor.getValue().getPlan()).isEqualTo("PlanA");
        assertThat(captor.getValue().getLastYearPremium()).isEqualByComparingTo(new BigDecimal("25448"));
    }

    @Test
    void importCustomers_matchingPhone_updatesExistingCustomerInstead() throws Exception {
        Customer existing = Customer.builder().id("cust-1").name("Old Name").phone("9876543210").build();
        when(customerRepository.searchByNameOrPhone("9876543210")).thenReturn(List.of(existing));

        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "New Name,PlanB,30000,07-12-2026,new@test.com,01-01-1980,9876543210\n"));

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getUpdatedCount()).isEqualTo(1);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("cust-1");
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
    }

    @Test
    void importCustomers_blankName_recordsRowErrorAndSkipsRow() throws Exception {
        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + ",PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,9876543210\n"));

        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Name is required");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void importCustomers_blankPhone_recordsRowError() throws Exception {
        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,\n"));

        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Phone is required");
    }

    @Test
    void importCustomers_phoneWithNoDigits_recordsInvalidPhoneError() throws Exception {
        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,N/A\n"));

        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Phone is invalid");
    }

    @Test
    void importCustomers_optionalColumnsMissing_stillImportsSuccessfully() throws Exception {
        // Real-world file has only the 7 required columns — Address/Notes (cols 7,8) are absent.
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,9876543210\n"));

        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getCreatedCount()).isEqualTo(1);
    }

    @Test
    void importCustomers_ddMMyyyyDate_parsesCorrectly() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        importService.importCustomers(csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,12-03-1974,9876543210\n"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 7));
        assertThat(captor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1974, 3, 12));
    }

    @Test
    void importCustomers_slashSeparatedUsDate_parsesCorrectly() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        importService.importCustomers(csv(HEADER + "Ajit Kumar,PlanA,25448,07-12-2026,ajit@test.com,11/28/1968,9876543210\n"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1968, 11, 28));
    }

    @Test
    void importCustomers_unparsableDate_leavesFieldNullRatherThanFailingRow() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        ImportResultResponse result = importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,25448,not-a-date,ajit@test.com,12-03-1974,9876543210\n"));

        assertThat(result.getFailureCount()).isZero();
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryDate()).isNull();
    }

    @Test
    void importCustomers_premiumWithCurrencySymbolsAndCommas_parsesTolerant() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        importService.importCustomers(
                csv(HEADER + "Ajit Kumar,PlanA,\"₹25,448.50\",07-12-2026,ajit@test.com,12-03-1974,9876543210\n"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getLastYearPremium()).isEqualByComparingTo(new BigDecimal("25448.50"));
    }

    @Test
    void importCustomers_multipleRows_mixOfCreateUpdateAndFailure() throws Exception {
        Customer existing = Customer.builder().id("cust-1").name("Existing").phone("9111111111").build();
        when(customerRepository.searchByNameOrPhone("9111111111")).thenReturn(List.of(existing));
        when(customerRepository.searchByNameOrPhone("9222222222")).thenReturn(List.of());

        String body = HEADER
                + "Row One,PlanA,1000,07-12-2026,a@test.com,01-01-1980,9111111111\n"  // update
                + "Row Two,PlanB,2000,07-12-2026,b@test.com,01-01-1980,9222222222\n"  // create
                + ",PlanC,3000,07-12-2026,c@test.com,01-01-1980,9333333333\n";        // fails: blank name

        ImportResultResponse result = importService.importCustomers(csv(body));

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(2);
    }

    // ── Excel (.xlsx) parsing ───────────────────────────────────────

    @Test
    void importCustomers_xlsxFile_parsesRowsSkippingHeader() throws Exception {
        when(customerRepository.searchByNameOrPhone(anyString())).thenReturn(List.of());

        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Customers");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 7; i++) header.createCell(i).setCellValue("Col" + i);

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("Excel Customer");
            data.createCell(1).setCellValue("PlanX");
            data.createCell(2).setCellValue(15000);
            data.createCell(3).setCellValue("07-12-2026");
            data.createCell(4).setCellValue("excel@test.com");
            data.createCell(5).setCellValue("01-01-1990");
            data.createCell(6).setCellValue("9555555555");

            wb.write(out);
            xlsx = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "customers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        ImportResultResponse result = importService.importCustomers(file);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Excel Customer");
        assertThat(captor.getValue().getPhone()).isEqualTo("9555555555");
    }
}
