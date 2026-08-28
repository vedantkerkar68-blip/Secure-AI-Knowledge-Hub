package com.sakh.service;

import com.sakh.dto.user.UpdateUserRequest;
import com.sakh.dto.user.UpdateUserStatusRequest;
import com.sakh.dto.user.UserListResponse;
import com.sakh.dto.user.UserProfileResponse;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for user management including profile access, role assignment
 * and account status controls enforced for administrators.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    public UserProfileResponse getCurrentUser() {
        User user = getCurrentUserEntity();
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
        User actor = getCurrentUserEntity();

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        if (request.getRoleId() != null) {
            if (actor.getId().equals(id)) {
                throw new IllegalArgumentException("You cannot change your own role.");
            }
            Role newRole = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.getRoleId()));
            if (isAdmin(user) && !newRole.getName().equals("ADMIN") && isLastActiveAdmin()) {
                throw new IllegalArgumentException("Cannot change the role of the last active administrator.");
            }
            user.setRole(newRole);
        }

        if (request.getStatus() != null) {
            validateStatusChange(user, UserStatus.valueOf(request.getStatus()), actor);
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
        User actor = getCurrentUserEntity();

        validateStatusChange(user, request.getStatus(), actor);

        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    private void validateStatusChange(User target, UserStatus newStatus, User actor) {
        if (actor.getId().equals(target.getId()) && newStatus != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        if (newStatus != UserStatus.ACTIVE && isAdmin(target) && isLastActiveAdmin()) {
            throw new IllegalArgumentException("Cannot deactivate the last active administrator.");
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN".equals(user.getRole().getName());
    }

    private boolean isLastActiveAdmin() {
        return userRepository.countByRoleNameAndStatus("ADMIN", UserStatus.ACTIVE) <= 1;
    }

    private User getCurrentUserEntity() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email;

        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .build();
    }

    private UserListResponse toListResponse(User user) {
        return UserListResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .build();
    }
}