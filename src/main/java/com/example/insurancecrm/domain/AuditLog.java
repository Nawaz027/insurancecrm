package com.example.insurancecrm.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    private String action;

    private String entityType;

    private String entityId;

    private String performedBy;

    private String performedByName;

    private String details;

    private LocalDateTime timestamp;
}
