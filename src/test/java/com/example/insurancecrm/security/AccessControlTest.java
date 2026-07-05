package com.example.insurancecrm.security;

import com.example.insurancecrm.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AccessControlTest {

    @Test
    void admin_isAlwaysAllowed_evenWhenNotTheOwner() {
        assertThatCode(() -> AccessControl.requireOwnerOrAdmin("agent-1", "admin-1", true))
                .doesNotThrowAnyException();
    }

    @Test
    void admin_isAllowed_evenWhenRecordIsUnassigned() {
        assertThatCode(() -> AccessControl.requireOwnerOrAdmin(null, "admin-1", true))
                .doesNotThrowAnyException();
    }

    @Test
    void owningAgent_isAllowed() {
        assertThatCode(() -> AccessControl.requireOwnerOrAdmin("agent-1", "agent-1", false))
                .doesNotThrowAnyException();
    }

    @Test
    void nonOwningAgent_isForbidden() {
        assertThatThrownBy(() -> AccessControl.requireOwnerOrAdmin("agent-1", "agent-2", false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void agent_isForbiddenFromUnassignedRecord() {
        // Unassigned (assignedAgentId == null) records are admin-only for non-admins
        assertThatThrownBy(() -> AccessControl.requireOwnerOrAdmin(null, "agent-1", false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
