package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportResultResponse {
    private int totalRows;
    private int successCount;
    private int createdCount;
    private int updatedCount;
    private int failureCount;
    private List<RowError> errors;

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String data;
        private String message;
    }
}
