package com.sakh.service;

import com.sakh.dto.auth.AuthResponse;
import com.sakh.dto.auth.LoginRequest;
import com.sakh.dto.auth.RegisterRequest;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.ActivityType;
import com.sakh.enums.UserStatus;
import com.sakh.exception.AccountNotActiveException;
import com.sakh.exception.DuplicateResourceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.JwtService;
import com.sakh.security.SakhUserDetails;
import com.sakh.security.TokenRevocationService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final ActivityLogService activityLogService;

    public AuthenticationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenRevocationService tokenRevocationService,
            ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRevocationService = tokenRevocationService;
        this.activityLogService = activityLogService;
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

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException("Your account is not active. Please contact an administrator.");
        }

        String token = jwtService.generateToken(toUserDetails(user));

        activityLogService.log(user, ActivityType.LOGIN, null);

        return new AuthResponse(token, "Bearer", 86400000);
    }

    /**
     * Revokes the provided token so it can no longer be used.
     */
    public void logout(String token, Long userId) {
        tokenRevocationService.revoke(token, userId, "LOGOUT");
        userRepository.findById(userId).ifPresent(user ->
                activityLogService.log(user, ActivityType.LOGOUT, null));
    }

    /**
     * Rotates the current access token: revokes the old one and issues a fresh
     * token with the same expiry window, provided the account is still active.
     */
    public AuthResponse refresh(String token) {
        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException("Your account is not active. Please contact an administrator.");
        }

        String jti = jwtService.extractTokenId(token);
        if (tokenRevocationService.isRevoked(jti)) {
            throw new ResourceNotFoundException("Invalid token");
        }

        if (jwtService.extractExpiration(token).before(new java.util.Date())) {
            throw new ResourceNotFoundException("Token has expired");
        }

        tokenRevocationService.revokeJti(jti, user.getId(), "ROTATION");

        String newToken = jwtService.generateToken(toUserDetails(user));

        activityLogService.log(user, ActivityType.LOGIN, null);

        return new AuthResponse(newToken, "Bearer", 86400000);
    }

    private UserDetails toUserDetails(User user) {
        return new SakhUserDetails(user);
    }
}