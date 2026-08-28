package com.sakh.service;

import com.sakh.dto.department.DepartmentRequest;
import com.sakh.dto.department.DepartmentResponse;
import com.sakh.entity.Department;
import com.sakh.exception.DuplicateResourceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Department already exists with name: " + request.getName());
        }
        Department department = new Department();
        department.setName(request.getName());
        department.setDescription(request.getDescription());
        department.setParent(resolveParent(request.getParentId()));
        Department saved = departmentRepository.save(department);
        return toResponse(saved);
    }

    public DepartmentResponse getDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        return toResponse(department);
    }

    public Page<DepartmentResponse> getAllDepartments(Pageable pageable) {
        return departmentRepository.findAll(pageable).map(this::toResponse);
    }

    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (!department.getName().equals(request.getName()) && departmentRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Department already exists with name: " + request.getName());
        }

        department.setName(request.getName());
        department.setDescription(request.getDescription());
        department.setParent(resolveParentForUpdate(department, request.getParentId()));
        return toResponse(departmentRepository.save(department));
    }

    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Department not found with id: " + id);
        }
        if (departmentRepository.existsByParentId(id)) {
            throw new IllegalArgumentException(
                    "This department has sub-departments. Move or delete them first.");
        }
        departmentRepository.deleteById(id);
    }

    /**
     * Returns all departments with their parent populated, ordered by id.
     */
    public List<Department> getAllDepartmentsList() {
        return departmentRepository.findAll();
    }

    /**
     * Returns the ids of a department and every department below it in the tree.
     */
    public List<Long> collectSubtreeIds(List<Department> all, Long rootId) {
        Map<Long, List<Department>> children = all.stream()
                .filter(d -> d.getParent() != null)
                .collect(Collectors.groupingBy(d -> d.getParent().getId()));
        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            for (Department child : children.getOrDefault(current, List.of())) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    /**
     * Returns the ids of a department and every department above it in the tree.
     */
    public List<Long> collectAncestorIds(List<Department> all, Long departmentId) {
        Map<Long, Department> byId = all.stream()
                .collect(Collectors.toMap(Department::getId, department -> department));
        Set<Long> visited = new HashSet<>();
        List<Long> result = new ArrayList<>();
        Department current = byId.get(departmentId);
        while (current != null && visited.add(current.getId())) {
            result.add(current.getId());
            current = current.getParent();
        }
        return result;
    }

    public DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .parentId(department.getParent() != null ? department.getParent().getId() : null)
                .createdAt(department.getCreatedAt())
                .build();
    }

    private Department resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return departmentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent department not found with id: " + parentId));
    }

    private Department resolveParentForUpdate(Department department, Long parentId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(department.getId())) {
            throw new IllegalArgumentException("A department cannot be its own parent");
        }
        Department parent = resolveParent(parentId);
        List<Long> descendantIds = collectSubtreeIds(getAllDepartmentsList(), parentId);
        if (descendantIds.contains(department.getId())) {
            throw new IllegalArgumentException(
                    "A department cannot be a parent of one of its own sub-departments");
        }
        return parent;
    }
}