-- Department tree: allow a department to have a parent (sub-department)
ALTER TABLE departments ADD COLUMN parent_id BIGINT REFERENCES departments(id);

-- Document visibility: ALL = visible to everyone, DEPARTMENT = department + sub-departments
ALTER TABLE documents ADD COLUMN access_scope VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT';
