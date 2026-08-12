package com.sandy.expense.web;

import com.sandy.expense.domain.RequestStatus;
import com.sandy.expense.security.AppUserPrincipal;
import com.sandy.expense.service.ExpenseService;
import com.sandy.expense.web.dto.ExpenseDtos.CreateRequest;
import com.sandy.expense.web.dto.ExpenseDtos.DecisionInput;
import com.sandy.expense.web.dto.ExpenseDtos.RequestDetail;
import com.sandy.expense.web.dto.ExpenseDtos.RequestSummary;
import com.sandy.expense.web.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetail create(
            @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody CreateRequest body) {
        return service.create(me, body);
    }

    /** Role-scoped list: EMPLOYEE=own, MANAGER=department, FINANCE=all. Optional status filter. */
    @GetMapping
    public PageResponse<RequestSummary> list(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.list(me, status, pageable);
    }

    @GetMapping("/{id}")
    public RequestDetail get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}")
    public RequestDetail update(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable Long id,
            @Valid @RequestBody CreateRequest body) {
        return service.update(me, id, body);
    }

    @PostMapping("/{id}/submit")
    public RequestDetail submit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.submit(me, id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")
    public RequestDetail approve(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionInput body) {
        return service.approve(me, id, body == null ? "" : body.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")
    public RequestDetail reject(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionInput body) {
        return service.reject(me, id, body == null ? "" : body.comment());
    }
}
