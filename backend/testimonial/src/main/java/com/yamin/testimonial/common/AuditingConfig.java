package com.yamin.testimonial.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so {@code createdAt}/{@code updatedAt} are populated
 * automatically.
 *
 * <p>{@link BaseEntity} sets the timestamps itself with Hibernate's
 * {@code @CreationTimestamp}/{@code @UpdateTimestamp}, so this configuration
 * exists mainly to keep the auditing infrastructure available for entities
 * that later need {@code @CreatedBy}/{@code @LastModifiedBy}.
 */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {
}
