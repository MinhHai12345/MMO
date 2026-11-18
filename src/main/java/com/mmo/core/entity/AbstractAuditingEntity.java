package com.mmo.core.entity;

/**
 * @deprecated Extend the infrastructure persistence auditing superclass.
 * Retained for compatibility while domain entities are migrated incrementally.
 */
@Deprecated(forRemoval = false)
public abstract class AbstractAuditingEntity
        extends com.mmo.infrastructure.persistence.entity.AbstractAuditingEntity {
}
