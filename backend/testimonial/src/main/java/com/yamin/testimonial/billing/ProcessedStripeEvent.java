package com.yamin.testimonial.billing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Stripe event id that has already been handled.
 *
 * <p>The event id is the primary key, so inserting a duplicate fails and the
 * webhook handler can treat that as "already processed". This makes webhook
 * processing idempotent and retry-safe (PRD 4.9, 6.14): replaying the same
 * event does not duplicate its effects.
 */
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent {

	@Id
	@Column(name = "event_id", nullable = false)
	private String eventId;

	@Column(name = "event_type")
	private String eventType;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	public ProcessedStripeEvent() {
	}

	public ProcessedStripeEvent(String eventId, String eventType, Instant processedAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.processedAt = processedAt;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(Instant processedAt) {
		this.processedAt = processedAt;
	}
}
