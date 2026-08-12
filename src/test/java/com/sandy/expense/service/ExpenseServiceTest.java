package com.sandy.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sandy.expense.domain.Department;
import com.sandy.expense.domain.ExpenseItem;
import com.sandy.expense.domain.ExpenseRequest;
import com.sandy.expense.domain.RequestStatus;
import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.ExpenseRequestRepository;
import com.sandy.expense.repo.UserRepository;
import com.sandy.expense.security.AppUserPrincipal;
import com.sandy.expense.web.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the authorization + state-machine rules in {@link ExpenseService}, with mocked
 * repositories (no database). These pin the three RBAC guarantees interviewers probe: the role
 * owns the stage, a manager is department-scoped, and nobody decides on their own request.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock ExpenseRequestRepository requests;
    @Mock UserRepository users;
    @InjectMocks ExpenseService service;

    private final Department eng = dept(1L, "Engineering");
    private final Department sales = dept(2L, "Sales");
    private final Department finDept = dept(3L, "Finance");

    private final User alice = user(10L, "Alice", Role.EMPLOYEE, eng);
    private final User bob = user(11L, "Bob", Role.MANAGER, eng);
    private final User dave = user(12L, "Dave", Role.MANAGER, sales);
    private final User carol = user(13L, "Carol", Role.EMPLOYEE, sales);
    private final User fiona = user(14L, "Fiona", Role.FINANCE, finDept);

    // ---------- approve: happy paths ----------

    @Test
    void managerApprovesSubmittedRequestInOwnDepartment() {
        ExpenseRequest r = request(100L, alice, eng, RequestStatus.SUBMITTED);
        when(requests.findById(100L)).thenReturn(Optional.of(r));
        when(users.getReferenceById(bob.getId())).thenReturn(bob);

        service.approve(principal(bob), 100L, "looks good");

        assertThat(r.getStatus()).isEqualTo(RequestStatus.MANAGER_APPROVED);
        assertThat(r.getApprovals()).hasSize(1);
        assertThat(r.getApprovals().get(0).getComment()).isEqualTo("looks good");
    }

    @Test
    void financeGivesFinalApprovalToManagerApprovedRequest() {
        ExpenseRequest r = request(101L, alice, eng, RequestStatus.MANAGER_APPROVED);
        when(requests.findById(101L)).thenReturn(Optional.of(r));
        when(users.getReferenceById(fiona.getId())).thenReturn(fiona);

        service.approve(principal(fiona), 101L, "reimbursed");

        assertThat(r.getStatus()).isEqualTo(RequestStatus.FINANCE_APPROVED);
    }

    // ---------- approve: the three RBAC guards ----------

    @Test
    void nobodyCanApproveTheirOwnRequest() {
        // Bob is a manager in Engineering AND the requester — still forbidden.
        ExpenseRequest r = request(102L, bob, eng, RequestStatus.SUBMITTED);
        when(requests.findById(102L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(principal(bob), 102L, ""))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("your own request");
        assertThat(r.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    }

    @Test
    void managerCannotDecideOnAnotherDepartmentsRequest() {
        ExpenseRequest r = request(103L, alice, eng, RequestStatus.SUBMITTED);
        when(requests.findById(103L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(principal(dave), 103L, ""))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("your own department");
    }

    @Test
    void wrongRoleForTheStageIsRejected() {
        // Finance cannot approve a SUBMITTED request — that stage belongs to a manager.
        ExpenseRequest r = request(104L, alice, eng, RequestStatus.SUBMITTED);
        when(requests.findById(104L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(principal(fiona), 104L, ""))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MANAGER");
    }

    @Test
    void cannotApproveARequestThatIsNotAwaitingADecision() {
        ExpenseRequest r = request(105L, alice, eng, RequestStatus.DRAFT);
        when(requests.findById(105L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(principal(bob), 105L, ""))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not awaiting");
    }

    // ---------- submit ----------

    @Test
    void ownerSubmitsDraftAndAnAuditRecordIsWritten() {
        ExpenseRequest r = request(106L, alice, eng, RequestStatus.DRAFT);
        r.addItem(item("Flight", "Travel", "420.00"));
        when(requests.findById(106L)).thenReturn(Optional.of(r));
        when(users.getReferenceById(alice.getId())).thenReturn(alice);

        service.submit(principal(alice), 106L);

        assertThat(r.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(r.getSubmittedAt()).isNotNull();
        assertThat(r.getApprovals()).hasSize(1);
    }

    @Test
    void onlyTheOwnerCanSubmit() {
        ExpenseRequest r = request(107L, alice, eng, RequestStatus.DRAFT);
        when(requests.findById(107L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.submit(principal(bob), 107L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("your own");
    }

    // ---------- read scoping ----------

    @Test
    void employeeCannotViewSomeoneElsesRequest() {
        ExpenseRequest r = request(108L, alice, eng, RequestStatus.SUBMITTED);
        when(requests.findById(108L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.get(principal(carol), 108L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    // ---------- fixtures ----------

    private Department dept(long id, String name) {
        Department d = new Department(name);
        d.setId(id);
        return d;
    }

    private User user(long id, String name, Role role, Department dept) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setEmail(name.toLowerCase() + "@acme.com");
        u.setPasswordHash("x");
        u.setRole(role);
        u.setDepartment(dept);
        return u;
    }

    private AppUserPrincipal principal(User u) {
        return AppUserPrincipal.fromUser(u);
    }

    private ExpenseRequest request(long id, User requester, Department dept, RequestStatus status) {
        ExpenseRequest r = new ExpenseRequest();
        r.setId(id);
        r.setRequester(requester);
        r.setDepartment(dept);
        r.setStatus(status);
        r.setTitle("Test request");
        r.setCurrency("USD");
        // findById is stubbed per test; getReferenceById only on the success paths.
        lenient().when(users.getReferenceById(anyLong())).thenReturn(requester);
        return r;
    }

    private ExpenseItem item(String desc, String category, String amount) {
        ExpenseItem i = new ExpenseItem();
        i.setDescription(desc);
        i.setCategory(category);
        i.setAmount(new BigDecimal(amount));
        i.setIncurredOn(LocalDate.now());
        return i;
    }
}
