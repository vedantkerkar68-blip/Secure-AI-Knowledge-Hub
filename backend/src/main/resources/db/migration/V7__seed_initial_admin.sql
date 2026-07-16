-- Secure AI Knowledge Hub (SAKH)
-- V7: Seed default department and administrator account
-- Runs only once on fresh database via Flyway

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO departments (name, description)
SELECT 'IT Department', 'Default system department'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'IT Department');

INSERT INTO users (first_name, last_name, email, password_hash, role_id, department_id, status, created_at, updated_at)
SELECT
    'Admin',
    'User',
    'admin@sakh.com',
    crypt('Admin@123', gen_salt('bf', 10)),
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    (SELECT id FROM departments WHERE name = 'IT Department'),
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@sakh.com');
