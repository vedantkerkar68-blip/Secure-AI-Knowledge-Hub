package com.sakh.service;

import com.sakh.dto.user.UpdateUserRequest;
import com.sakh.dto.user.UpdateUserStatusRequest;
import com.sakh.dto.user.UserListResponse;
import com.sakh.dto.user.UserProfileResponse;
import com.sakh.entity.Department;
import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public UserService(UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    public UserProfileResponse getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email;

        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return toResponse(user);
    }

    public UserProfileResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return toResponse(user);
    }

    public Page<UserListResponse> getAllUsers(String search, String role, String department, UserStatus status, Pageable pageable) {
        Page<User> users = userRepository.findWithFilters(search, role, department, status, pageable);
        return users.map(this::toListResponse);
    }

    public UserProfileResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        if (request.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(request.getStatus()));
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            user.setDepartment(department);
        } else {
            user.setDepartment(null);
        }

        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public UserProfileResponse updateUserStatus(Long id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .build();
    }

    private UserListResponse toListResponse(User user) {
        return UserListResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .build();
    }
}