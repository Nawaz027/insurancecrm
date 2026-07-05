package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReminderResponse {

    public enum ReminderType { LEAD_FOLLOWUP, COMMUNICATION_FOLLOWUP }

    private String id;
    private ReminderType type;
    private String entityId;
    private String entityName;   // customer or lead name
    private String description;
    private LocalDate dueDate;
    private long overdueDays;    // 0 = due today, >0 = overdue
    private String assignedToId;
    private String assignedToName;
}
