package com.sandy.expense.repo;

import com.sandy.expense.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {}
