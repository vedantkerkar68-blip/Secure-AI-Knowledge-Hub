package com.sakh.controller;

import com.sakh.dto.auth.AuthResponse;
import com.sakh.dto.auth.LoginRequest;
import com.sakh.dto.auth.RegisterRequest;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;

    public AuthenticationController(AuthenticationService authenticationService,
                                    RoleRepository roleRepository,
                                    DepartmentRepository departmentRepository) {
        this.authenticationService = authenticationService;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid role ID: " + request.getRoleId()));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid department ID: " + request.getDepartmentId()));
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setRole(role);
        user.setDepartment(department);

        String token = authenticationService.register(user, request.getPassword());

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(86400000)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authenticationService.login(request.getEmail(), request.getPassword());

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(86400000)
                .build();

        return ResponseEntity.ok(response);
    }
}