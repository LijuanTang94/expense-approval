package com.sandy.expense.domain;

/** A user's single role. Spring Security authorities are derived as "ROLE_" + name(). */
public enum Role {
    EMPLOYEE,
    MANAGER,
    FINANCE,
}
