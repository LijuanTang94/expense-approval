package com.sandy.expense.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "expense_requests")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic lock. Two approvers acting on the same request concurrently would otherwise both
     * read the same status, both pass the transition check, and both commit — producing a
     * contradictory audit trail (e.g. an approve and a reject recorded for one decision). With a
     * version column the second commit fails and is surfaced as a 409.
     */
    @Version
    @Column(nullable = false)
    private long version;

    // LAZY, not EAGER: the list endpoint pages over requests and maps them to DTOs, so EAGER
    // to-one associations produced an N+1 (1 + 2N queries per page). The repository fetches these
    // explicitly with an @EntityGraph where they're needed.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ExpenseItem> items = new ArrayList<>();

    // Ordered by id, not createdAt: two transitions can share a timestamp, which would make the
    // audit trail's order nondeterministic. Insertion order is monotonic and stable.
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Approval> approvals = new ArrayList<>();

    /**
     * Recompute the denormalised total from the current line items. Item amounts are already
     * normalised to 2dp on the way in, and the sum is pinned to the same scale, so the value
     * returned in the response is exactly what the NUMERIC(12,2) column stores.
     */
    public void recomputeTotal() {
        this.totalAmount =
                items.stream()
                        .map(ExpenseItem::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
    }

    public void addItem(ExpenseItem item) {
        item.setRequest(this);
        this.items.add(item);
    }
}
