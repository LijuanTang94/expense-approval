package com.sandy.expense.service;

import com.sandy.expense.domain.Approval;
import com.sandy.expense.domain.ApprovalAction;
import com.sandy.expense.domain.ExpenseItem;
import com.sandy.expense.domain.ExpenseRequest;
import com.sandy.expense.domain.RequestStatus;
import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.ExpenseRequestRepository;
import com.sandy.expense.repo.UserRepository;
import com.sandy.expense.security.AppUserPrincipal;
import com.sandy.expense.web.dto.ExpenseDtos.CreateRequest;
import com.sandy.expense.web.dto.ExpenseDtos.ItemInput;
import com.sandy.expense.web.dto.ExpenseDtos.RequestDetail;
import com.sandy.expense.web.dto.ExpenseDtos.RequestSummary;
import com.sandy.expense.web.dto.PageResponse;
import com.sandy.expense.web.error.ApiException;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRequestRepository requests;
    private final UserRepository users;

    public ExpenseService(ExpenseRequestRepository requests, UserRepository users) {
        this.requests = requests;
        this.users = users;
    }

    // ---- create (DRAFT) ----

    @Transactional
    public RequestDetail create(AppUserPrincipal me, CreateRequest input) {
        User requester =
                users.findById(me.getUserId())
                        .orElseThrow(() -> ApiException.notFound("User not found"));
        if (requester.getDepartment() == null) {
            throw ApiException.badRequest("NO_DEPARTMENT", "Your account has no department; ask an admin to assign one");
        }
        ExpenseRequest r = new ExpenseRequest();
        r.setRequester(requester);
        r.setDepartment(requester.getDepartment());
        r.setTitle(input.title());
        r.setDescription(input.description() == null ? "" : input.description());
        r.setCurrency(input.currency() == null || input.currency().isBlank() ? "USD" : input.currency().toUpperCase());
        r.setStatus(RequestStatus.DRAFT);
        for (ItemInput it : input.items()) {
            r.addItem(toItem(it));
        }
        r.recomputeTotal();
        requests.save(r);
        return RequestDetail.of(r);
    }

    private ExpenseItem toItem(ItemInput it) {
        ExpenseItem item = new ExpenseItem();
        item.setDescription(it.description());
        item.setCategory(it.category());
        // Pin to the column's scale here rather than letting the driver round on write: otherwise
        // the object returned in the create/update response carries the raw value and disagrees
        // with what a subsequent read returns.
        item.setAmount(it.amount().setScale(2, RoundingMode.HALF_UP));
        item.setIncurredOn(it.incurredOn());
        return item;
    }

    // ---- read (role-scoped) ----

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> list(AppUserPrincipal me, RequestStatus status, Pageable pageable) {
        Page<ExpenseRequest> page =
                switch (me.getRole()) {
                    case EMPLOYEE -> requests.forRequester(me.getUserId(), status, pageable);
                    case MANAGER -> requests.forDepartment(requireDept(me), status, pageable);
                    case FINANCE -> requests.forAll(status, pageable);
                };
        return PageResponse.of(page, RequestSummary::of);
    }

    @Transactional(readOnly = true)
    public RequestDetail get(AppUserPrincipal me, Long id) {
        // Fetches requester + department in the same query (the DTO reads both).
        ExpenseRequest r =
                requests.findWithDetailById(id)
                        .orElseThrow(() -> ApiException.notFound("Request not found"));
        assertCanView(me, r);
        // touch lazy collections inside the tx
        r.getItems().size();
        r.getApprovals().size();
        return RequestDetail.of(r);
    }

    // ---- update (owner, DRAFT only) ----

    @Transactional
    public RequestDetail update(AppUserPrincipal me, Long id, CreateRequest input) {
        ExpenseRequest r = load(id);
        if (!r.getRequester().getId().equals(me.getUserId())) {
            throw ApiException.forbidden("You can only edit your own requests");
        }
        if (r.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.conflict("NOT_EDITABLE", "Only DRAFT requests can be edited");
        }
        r.setTitle(input.title());
        r.setDescription(input.description() == null ? "" : input.description());
        if (input.currency() != null && !input.currency().isBlank()) {
            r.setCurrency(input.currency().toUpperCase());
        }
        r.getItems().clear();
        for (ItemInput it : input.items()) {
            r.addItem(toItem(it));
        }
        r.recomputeTotal();
        return RequestDetail.of(r);
    }

    // ---- transitions ----

    @Transactional
    public RequestDetail submit(AppUserPrincipal me, Long id) {
        ExpenseRequest r = load(id);
        if (!r.getRequester().getId().equals(me.getUserId())) {
            throw ApiException.forbidden("You can only submit your own requests");
        }
        if (r.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.conflict("NOT_SUBMITTABLE", "Only DRAFT requests can be submitted");
        }
        if (r.getItems().isEmpty()) {
            throw ApiException.badRequest("EMPTY_REQUEST", "Add at least one line item before submitting");
        }
        transition(r, me, ApprovalAction.SUBMIT, RequestStatus.SUBMITTED, "");
        r.setSubmittedAt(Instant.now());
        return RequestDetail.of(r);
    }

    @Transactional
    public RequestDetail approve(AppUserPrincipal me, Long id, String comment) {
        ExpenseRequest r = load(id);
        assertCanDecide(me, r);
        RequestStatus next = r.getStatus().onApprove();
        transition(r, me, ApprovalAction.APPROVE, next, comment);
        return RequestDetail.of(r);
    }

    @Transactional
    public RequestDetail reject(AppUserPrincipal me, Long id, String comment) {
        ExpenseRequest r = load(id);
        assertCanDecide(me, r);
        transition(r, me, ApprovalAction.REJECT, RequestStatus.REJECTED, comment);
        return RequestDetail.of(r);
    }

    // ---- helpers: authorization + state machine ----

    /**
     * Whoever decides on a request must (1) hold the role that owns the request's current stage,
     * (2) for a MANAGER, be in the same department, and (3) never be the requester themselves.
     */
    private void assertCanDecide(AppUserPrincipal me, ExpenseRequest r) {
        Role needed = r.getStatus().approverRole();
        if (needed == null) {
            throw ApiException.conflict("NOT_PENDING", "This request is not awaiting a decision");
        }
        if (me.getRole() != needed) {
            throw ApiException.forbidden("This stage must be decided by a " + needed);
        }
        if (needed == Role.MANAGER && !r.getDepartment().getId().equals(me.getDepartmentId())) {
            throw ApiException.forbidden("You can only decide on requests in your own department");
        }
        if (r.getRequester().getId().equals(me.getUserId())) {
            throw ApiException.forbidden("You cannot decide on your own request");
        }
    }

    private void assertCanView(AppUserPrincipal me, ExpenseRequest r) {
        boolean ok =
                switch (me.getRole()) {
                    case EMPLOYEE -> r.getRequester().getId().equals(me.getUserId());
                    case MANAGER -> r.getDepartment().getId().equals(me.getDepartmentId());
                    case FINANCE -> true;
                };
        if (!ok) {
            // 404 rather than 403 so we don't leak the existence of other departments' requests.
            throw ApiException.notFound("Request not found");
        }
    }

    /** Apply a state change and append an immutable approval-trail record. */
    private void transition(
            ExpenseRequest r, AppUserPrincipal me, ApprovalAction action, RequestStatus to, String comment) {
        Approval a = new Approval();
        a.setRequest(r);
        a.setActor(users.getReferenceById(me.getUserId()));
        a.setAction(action);
        a.setFromStatus(r.getStatus());
        a.setToStatus(to);
        a.setComment(comment == null ? "" : comment);
        r.getApprovals().add(a);
        r.setStatus(to);
    }

    private ExpenseRequest load(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Request not found"));
    }

    private Long requireDept(AppUserPrincipal me) {
        if (me.getDepartmentId() == null) {
            throw ApiException.badRequest("NO_DEPARTMENT", "Your account has no department");
        }
        return me.getDepartmentId();
    }
}
