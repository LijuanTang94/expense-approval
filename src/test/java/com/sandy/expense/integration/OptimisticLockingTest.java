package com.sandy.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sandy.expense.domain.ExpenseRequest;
import com.sandy.expense.domain.RequestStatus;
import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.ExpenseRequestRepository;
import com.sandy.expense.repo.UserRepository;
import com.sandy.expense.security.AppUserPrincipal;
import com.sandy.expense.service.ExpenseService;
import com.sandy.expense.web.dto.ExpenseDtos.CreateRequest;
import com.sandy.expense.web.dto.ExpenseDtos.ItemInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Two approvers acting on the same request at the same time.
 *
 * <p>Approving is read-then-write: load the request, check the caller may decide, then move the
 * state and append to the audit trail. Without a version column, two managers who both loaded the
 * request while it was SUBMITTED would both pass the check and both write — advancing the state
 * twice and leaving two rows in an audit trail that is supposed to record what actually happened.
 *
 * <p>The {@code @Version} column turns that into a detectable conflict: whoever commits second is
 * writing against a row whose version has moved, so the UPDATE matches nothing and JPA raises
 * {@link ObjectOptimisticLockingFailureException}, which the API surfaces as 409.
 */
class OptimisticLockingTest extends PostgresIntegrationTest {

    @Autowired ExpenseService service;
    @Autowired ExpenseRequestRepository requests;
    @Autowired UserRepository users;

    private AppUserPrincipal principal(String email) {
        User u = users.findByEmail(email).orElseThrow();
        return new AppUserPrincipal(
                u.getId(),
                u.getEmail(),
                null,
                u.getRole(),
                u.getDepartment() == null ? null : u.getDepartment().getId());
    }

    /** A request sitting in SUBMITTED, filed by Alice and awaiting her manager. */
    private Long submittedRequest() {
        AppUserPrincipal alice = principal("alice@acme.com");
        var created =
                service.create(
                        alice,
                        new CreateRequest(
                                "Conference travel",
                                "flights + hotel",
                                "USD",
                                List.of(
                                        new ItemInput(
                                                "Flight", "TRAVEL", new BigDecimal("420.00"), LocalDate.now()))));
        service.submit(alice, created.id());
        return created.id();
    }

    @Test
    void secondApproverOnTheSameRequestLosesTheRace() {
        Long id = submittedRequest();
        AppUserPrincipal bob = principal("bob@acme.com");

        // Both approvers loaded version 0. Simulate that by detaching a copy before the first
        // commit moves the row on.
        ExpenseRequest asSeenBySecondApprover = requests.findById(id).orElseThrow();
        long versionBothApproversSaw = asSeenBySecondApprover.getVersion();

        service.approve(bob, id, "approved");

        assertThat(requests.findById(id).orElseThrow().getVersion())
                .as("the winning commit must bump the version")
                .isGreaterThan(versionBothApproversSaw);

        // The loser is now writing against a stale version.
        assertThatThrownBy(() -> requests.saveAndFlush(asSeenBySecondApprover))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void theWinningApprovalAdvancesTheStateExactlyOnce() {
        Long id = submittedRequest();
        service.approve(principal("bob@acme.com"), id, "approved");

        ExpenseRequest after = requests.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RequestStatus.MANAGER_APPROVED);

        // A second decision on the same request is no longer Bob's to make: the stage now belongs
        // to finance, so this fails on the state machine rather than on the version column.
        assertThatThrownBy(() -> service.approve(principal("bob@acme.com"), id, "again"))
                .hasMessageContaining("FINANCE");
    }

    @Test
    void rejectIsAlsoGuardedByStageOwnership() {
        Long id = submittedRequest();

        // Finance cannot reject while the request is still the manager's to decide.
        assertThatThrownBy(() -> service.reject(principal("fiona@acme.com"), id, "no"))
                .hasMessageContaining("MANAGER");

        service.reject(principal("bob@acme.com"), id, "not this quarter");
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.REJECTED);

        // REJECTED is terminal: approverRole() is null there, so nobody is awaiting a decision.
        assertThatThrownBy(() -> service.approve(principal("fiona@acme.com"), id, "actually yes"))
                .hasMessageContaining("not awaiting a decision");
    }

    @Test
    void aManagerFromAnotherDepartmentCannotDecide() {
        Long id = submittedRequest(); // Alice is in Engineering
        assertThatThrownBy(() -> service.reject(principal("dave@acme.com"), id, "nope"))
                .hasMessageContaining("your own department");
    }
}
