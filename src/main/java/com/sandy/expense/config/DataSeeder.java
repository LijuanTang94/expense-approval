package com.sandy.expense.config;

import com.sandy.expense.domain.Department;
import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.DepartmentRepository;
import com.sandy.expense.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds departments and demo accounts on first boot so the app is usable immediately.
 * All demo users share the password "password123". No-op once any user exists.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final DepartmentRepository departments;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(DepartmentRepository departments, UserRepository users, PasswordEncoder encoder) {
        this.departments = departments;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        Department eng = departments.save(new Department("Engineering"));
        Department sales = departments.save(new Department("Sales"));
        Department finance = departments.save(new Department("Finance"));

        seedUser("alice@acme.com", "Alice Employee", Role.EMPLOYEE, eng);
        seedUser("bob@acme.com", "Bob Manager", Role.MANAGER, eng);
        seedUser("carol@acme.com", "Carol Employee", Role.EMPLOYEE, sales);
        seedUser("dave@acme.com", "Dave Manager", Role.MANAGER, sales);
        seedUser("fiona@acme.com", "Fiona Finance", Role.FINANCE, finance);
    }

    private void seedUser(String email, String fullName, Role role, Department dept) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("password123"));
        u.setFullName(fullName);
        u.setRole(role);
        u.setDepartment(dept);
        users.save(u);
    }
}
