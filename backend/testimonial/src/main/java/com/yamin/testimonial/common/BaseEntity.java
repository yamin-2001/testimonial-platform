package com.yamin.testimonial.common;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared identity and creation timestamp for every persistent entity.
 *
 * <p>Identifiers are generated in the application (UUID v4) rather than by the
 * database, so an entity is usable before it is persisted.
 *
 * <p>Timestamps are {@link Instant}, which maps to {@code timestamptz}. The
 * datasource session sets {@code hibernate.time_zone: UTC}, so values are
 * stored and compared in UTC -- which is what the dashboard's daily buckets
 * rely on (PRD 4.6).
 */
@MappedSuperclass
public abstract class BaseEntity {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
