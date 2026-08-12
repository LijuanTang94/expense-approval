package com.sandy.expense.web.dto;

import com.sandy.expense.domain.Approval;
import com.sandy.expense.domain.ExpenseItem;
import com.sandy.expense.domain.ExpenseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Expense-request request/response payloads. */
public final class ExpenseDtos {

    private ExpenseDtos() {}

    // ---- inputs ----

    public record ItemInput(
            @NotBlank String description,
            @NotBlank String category,
            @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate incurredOn) {}

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @Size(min = 3, max = 3) String currency,
            @NotEmpty(message = "at least one line item is required") @Valid List<ItemInput> items) {}

    public record DecisionInput(@Size(max = 500) String comment) {}

    // ---- views ----

    public record ItemView(
            Long id, String description, String category, BigDecimal amount, LocalDate incurredOn) {
        public static ItemView of(ExpenseItem i) {
            return new ItemView(i.getId(), i.getDescription(), i.getCategory(), i.getAmount(), i.getIncurredOn());
        }
    }

    public record ApprovalView(
            Long id,
            Long actorId,
            String actorName,
            String action,
            String fromStatus,
            String toStatus,
            String comment,
            Instant createdAt) {
        public static ApprovalView of(Approval a) {
            return new ApprovalView(
                    a.getId(),
                    a.getActor().getId(),
                    a.getActor().getFullName(),
                    a.getAction().name(),
                    a.getFromStatus().name(),
                    a.getToStatus().name(),
                    a.getComment(),
                    a.getCreatedAt());
        }
    }

    /** Compact row for list views. */
    public record RequestSummary(
            Long id,
            String title,
            String status,
            BigDecimal totalAmount,
            String currency,
            String requesterName,
            String departmentName,
            Instant createdAt) {
        public static RequestSummary of(ExpenseRequest r) {
            return new RequestSummary(
                    r.getId(),
                    r.getTitle(),
                    r.getStatus().name(),
                    r.getTotalAmount(),
                    r.getCurrency(),
                    r.getRequester().getFullName(),
                    r.getDepartment().getName(),
                    r.getCreatedAt());
        }
    }

    /** Full detail including line items and the approval trail. */
    public record RequestDetail(
            Long id,
            String title,
            String description,
            String status,
            BigDecimal totalAmount,
            String currency,
            Long requesterId,
            String requesterName,
            String departmentName,
            Instant submittedAt,
            Instant createdAt,
            List<ItemView> items,
            List<ApprovalView> approvals) {
        public static RequestDetail of(ExpenseRequest r) {
            return new RequestDetail(
                    r.getId(),
                    r.getTitle(),
                    r.getDescription(),
                    r.getStatus().name(),
                    r.getTotalAmount(),
                    r.getCurrency(),
                    r.getRequester().getId(),
                    r.getRequester().getFullName(),
                    r.getDepartment().getName(),
                    r.getSubmittedAt(),
                    r.getCreatedAt(),
                    r.getItems().stream().map(ItemView::of).toList(),
                    r.getApprovals().stream().map(ApprovalView::of).toList());
        }
    }
}
