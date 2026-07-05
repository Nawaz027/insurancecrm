package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateCommunicationLogRequest;
import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunicationLogServiceTest {

    @Mock private CommunicationLogRepository logRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CommunicationLogService communicationLogService;

    private final User agent = User.builder().id("agent-1").name("Agent One").build();

    private CreateCommunicationLogRequest req(CommunicationOutcome outcome) {
        CreateCommunicationLogRequest r = new CreateCommunicationLogRequest();
        r.setChannel(CommunicationChannel.CALL);
        r.setOutcome(outcome);
        return r;
    }

    // ── logForCustomer — regression test for "customer outcomes invisible on Agent Performance" ──

    @Test
    void logForCustomer_updatesCustomerLastOutcome() {
        Customer customer = Customer.builder().id("cust-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.RINGING), "agent-1");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getLastOutcome()).isEqualTo(CommunicationOutcome.RINGING);
    }

    @Test
    void logForCustomer_savesLogWithCorrectCustomerIdAndLoggedBy() {
        Customer customer = Customer.builder().id("cust-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.CALLBACK), "agent-1");

        assertThat(response.getCustomerId()).isEqualTo("cust-1");
        assertThat(response.getLeadId()).isNull();
        assertThat(response.getLoggedBy()).isEqualTo("agent-1");
        assertThat(response.getLoggedByName()).isEqualTo("Agent One");
        assertThat(response.getOutcome()).isEqualTo(CommunicationOutcome.CALLBACK);
    }

    @Test
    void logForCustomer_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.logForCustomer("missing", req(CommunicationOutcome.RINGING), "agent-1"))
                .isInstanceOf(ApiException.class);
        verify(logRepository, never()).save(any());
    }

    // ── logForLead ────────────────────────────────────────────────────

    @Test
    void logForLead_updatesLeadLastOutcome() {
        Lead lead = Lead.builder().id("lead-1").build();
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        communicationLogService.logForLead("lead-1", req(CommunicationOutcome.PROSPECT), "agent-1");

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getLastOutcome()).isEqualTo(CommunicationOutcome.PROSPECT);
    }

    @Test
    void logForLead_missingLead_throwsNotFound() {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.logForLead("missing", req(CommunicationOutcome.RINGING), "agent-1"))
                .isInstanceOf(ApiException.class);
    }

    // ── getByCustomer / getByLead ─────────────────────────────────────

    @Test
    void getByCustomer_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.getByCustomer("missing")).isInstanceOf(ApiException.class);
    }

    @Test
    void getByLead_missingLead_throwsNotFound() {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.getByLead("missing")).isInstanceOf(ApiException.class);
    }

    // ── delete ──────────────────────────────────────────────────────

    @Test
    void delete_ownLog_succeeds() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        communicationLogService.delete("log-1", "agent-1", false);

        verify(logRepository).delete(log);
    }

    @Test
    void delete_otherAgentsLog_isForbiddenForNonAdmin() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> communicationLogService.delete("log-1", "agent-2", false))
                .isInstanceOf(ApiException.class);
        verify(logRepository, never()).delete(any());
    }

    @Test
    void delete_admin_canDeleteAnyAgentsLog() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        communicationLogService.delete("log-1", "admin-1", true);

        verify(logRepository).delete(log);
    }
}
