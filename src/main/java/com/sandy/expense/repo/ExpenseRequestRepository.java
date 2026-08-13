package com.sandy.expense.repo;

import com.sandy.expense.domain.ExpenseRequest;
import com.sandy.expense.domain.RequestStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

    // Role-scoped listings with an optional status filter (":status is null" = no filter).
    //
    // Each carries an @EntityGraph for requester + department because the summary DTO reads both.
    // Without it these were an N+1: one query for the page, then two more per row. Only to-one
    // associations are fetched here, so pagination still happens in the database (fetching a
    // to-many alongside Pageable would force Hibernate to paginate in memory).

    /** EMPLOYEE: only their own requests. */
    @EntityGraph(attributePaths = {"requester", "department"})
    @Query("select r from ExpenseRequest r "
            + "where r.requester.id = :userId and (:status is null or r.status = :status)")
    Page<ExpenseRequest> forRequester(
            @Param("userId") Long userId, @Param("status") RequestStatus status, Pageable pageable);

    /** MANAGER: everything in their department. */
    @EntityGraph(attributePaths = {"requester", "department"})
    @Query("select r from ExpenseRequest r "
            + "where r.department.id = :deptId and (:status is null or r.status = :status)")
    Page<ExpenseRequest> forDepartment(
            @Param("deptId") Long deptId, @Param("status") RequestStatus status, Pageable pageable);

    /** FINANCE: everything, org-wide. */
    @EntityGraph(attributePaths = {"requester", "department"})
    @Query("select r from ExpenseRequest r where (:status is null or r.status = :status)")
    Page<ExpenseRequest> forAll(@Param("status") RequestStatus status, Pageable pageable);

    /**
     * Detail view: pull the to-one associations up front so rendering the DTO doesn't fan out into
     * per-row queries. The items/approvals collections are still loaded lazily (two extra queries,
     * not one per row), which is the right trade for a single-record view.
     */
    @EntityGraph(attributePaths = {"requester", "department"})
    Optional<ExpenseRequest> findWithDetailById(Long id);
}
