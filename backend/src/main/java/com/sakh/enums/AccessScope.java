package com.sakh.enums;

/**
 * Represents who can see a document.
 * ALL - every authenticated user regardless of department.
 * DEPARTMENT - users in the owning department and all of its sub-departments.
 */
public enum AccessScope {

    ALL,
    DEPARTMENT
}
