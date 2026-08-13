package com.sandy.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The approval state machine — pure logic, no Spring. */
class RequestStatusTest {

    @Test
    void submittedIsOwnedByManagerAndAdvancesToManagerApproved() {
        assertThat(RequestStatus.SUBMITTED.approverRole()).isEqualTo(Role.MANAGER);
        assertThat(RequestStatus.SUBMITTED.onApprove()).isEqualTo(RequestStatus.MANAGER_APPROVED);
    }

    @Test
    void managerApprovedIsOwnedByFinanceAndAdvancesToFinanceApproved() {
        assertThat(RequestStatus.MANAGER_APPROVED.approverRole()).isEqualTo(Role.FINANCE);
        assertThat(RequestStatus.MANAGER_APPROVED.onApprove()).isEqualTo(RequestStatus.FINANCE_APPROVED);
    }

    @Test
    void draftAndTerminalStatesAwaitNoApproval() {
        assertThat(RequestStatus.DRAFT.approverRole()).isNull();
        assertThat(RequestStatus.DRAFT.onApprove()).isNull();
        assertThat(RequestStatus.FINANCE_APPROVED.approverRole()).isNull();
        assertThat(RequestStatus.REJECTED.approverRole()).isNull();
    }
}
