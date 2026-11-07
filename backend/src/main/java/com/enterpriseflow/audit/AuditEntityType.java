package com.enterpriseflow.audit;

/** Kind of entity an audit entry refers to; together with the entity id it identifies the record. */
public enum AuditEntityType {
    USER,
    PROJECT,
    TASK,
    APPROVAL
}
