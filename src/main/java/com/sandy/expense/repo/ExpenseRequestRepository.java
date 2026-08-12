package com.sandy.expense.repo;

import com.sandy.expense.domain.ExpenseRequest;
import com.sandy.expense.domain.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

    // Role-scoped listings with an optional status filter (":status is null" = no filter).

    /** EMPLOYEE: only their own requests. */
    @Query("select r from ExpenseRequest r "
            + "where r.requester.id = :userId and (:status is null or r.status = :status)")
    Page<ExpenseRequest> forRequester(
            @Param("userId") Long userId, @Param("status") RequestStatus status, Pageable pageable);

    /** MANAGER: everything in their department. */
    @Query("select r from ExpenseRequest r "
            + "where r.department.id = :deptId and (:status is null or r.status = :status)")
    Page<ExpenseRequest> forDepartment(
            @Param("deptId") Long deptId, @Param("status") RequestStatus status, Pageable pageable);

    /** FINANCE: everything, org-wide. */
    @Query("select r from ExpenseRequest r where (:status is null or r.status = :status)")
    Page<ExpenseRequest> forAll(@Param("status") RequestStatus status, Pageable pageable);
}
