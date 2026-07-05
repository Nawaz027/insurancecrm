package com.example.insurancecrm.security;

import com.example.insurancecrm.exception.ApiException;

import java.util.Objects;

/**
 * Object-level authorization helper. Agents may only read or mutate records
 * assigned to them; admins may access everything. Unassigned records
 * (assignedAgentId == null) are therefore admin-only for non-admins.
 */
public final class AccessControl {

    private AccessControl() {}

    public static void requireOwnerOrAdmin(String assignedAgentId, String currentUserId, boolean isAdmin) {
        if (!isAdmin && !Objects.equals(assignedAgentId, currentUserId)) {
            throw ApiException.forbidden("You do not have access to this record");
        }
    }
}
