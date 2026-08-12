package com.sandy.expense.web;

import com.sandy.expense.repo.DepartmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departments;

    public DepartmentController(DepartmentRepository departments) {
        this.departments = departments;
    }

    public record DepartmentView(Long id, String name) {}

    /** Read-only list used by the registration form's department dropdown. */
    @GetMapping
    public List<DepartmentView> list() {
        return departments.findAll().stream()
                .map(d -> new DepartmentView(d.getId(), d.getName()))
                .toList();
    }
}
