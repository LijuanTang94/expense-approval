package com.sandy.expense.domain;

import java.util.Set;

/**
 * States of an expense request and the legal transitions between them:
 *
 * <pre>
 *   DRAFT --submit--> SUBMITTED --approve--> MANAGER_APPROVED --approve--> FINANCE_APPROVED
 *                         \--reject--> REJECTED   <--reject--/
 * </pre>
 *
 * The allowed next-states live here so the state machine has one source of truth.
 */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    MANAGER_APPROVED,
    FINANCE_APPROVED,
    REJECTED;

    /** Terminal states cannot transition further. */
    public boolean isTerminal() {
        return this == FINANCE_APPROVED || this == REJECTED;
    }

    /** The stage that is allowed to approve/reject a request in this state (null if none). */
    public Role approverRole() {
        return switch (this) {
            case SUBMITTED -> Role.MANAGER;
            case MANAGER_APPROVED -> Role.FINANCE;
            default -> null;
        };
    }

    /** The next state on a successful approval (null if this state isn't awaiting approval). */
    public RequestStatus onApprove() {
        return switch (this) {
            case SUBMITTED -> MANAGER_APPROVED;
            case MANAGER_APPROVED -> FINANCE_APPROVED;
            default -> null;
        };
    }

    /** States from which a request may still be rejected. */
    public static final Set<RequestStatus> REJECTABLE = Set.of(SUBMITTED, MANAGER_APPROVED);
}
