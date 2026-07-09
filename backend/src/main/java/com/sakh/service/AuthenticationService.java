package com.sakh.service;

import com.sakh.dto.auth.AuthResponse;
import com.sakh.dto.auth.LoginRequest;
import com.sakh.dto.auth.RegisterRequest;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import com.sakh.exception.DuplicateResourceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.JwtService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + request.getEmail());
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.getRoleId()));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setDepartment(department);
        user.setStatus(UserStatus.ACTIVE);

        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(toUserDetails(savedUser));

        return new AuthResponse(token, "Bearer", 86400000);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        String token = jwtService.generateToken(toUserDetails(user));
        return new AuthResponse(token, "Bearer", 86400000);
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().getName())))
                .build();
    }
}