package com.yamin.testimonial.common;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Base class for entities that can be edited after creation.
 *
 * <p>Adds the {@code updated_at} column that {@link BaseEntity} deliberately
 * leaves out. Entities that are immutable once written -- the verification and
 * reset tokens -- extend {@link BaseEntity} directly, so their tables carry no
 * column they cannot populate.
 */
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
