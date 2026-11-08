package com.yamin.testimonial.spaces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A testimonial collection page with its own public URL and form.
 *
 * <p>Each Space belongs to exactly one user and has its own public URL and
 * form configuration (PRD 6.2, 6.3).
 *
 * <p>The three configuration blobs are stored as JSONB because their shape is
 * owned by the application layer and evolves without a schema migration:
 * <ul>
 *   <li>{@code formConfig} -- which optional fields the public form collects
 *       and which of them are required (PRD 4.3).</li>
 *   <li>{@code embedConfig} -- the embed widget's layout and options (PRD 4.8).</li>
 *   <li>{@code publicProfileFields} -- which submitter fields the embed may
 *       expose. Deliberately independent of {@code formConfig}: "may we ask
 *       for it" and "may we publish it" are different questions.</li>
 * </ul>
 */
@Entity
@Table(name = "spaces")
public class Space extends AuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser owner;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "subtitle")
	private String subtitle;

	/** Unique across the platform; forms the public URL /s/{slug} (PRD 4.2). */
	@Column(name = "slug", nullable = false)
	private String slug;

	/** Identifies one of the predefined themes (PRD 4.4). */
	@Column(name = "theme_key", nullable = false)
	private String themeKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "form_config", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> formConfig = new LinkedHashMap<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "embed_config", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> embedConfig = new LinkedHashMap<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "public_profile_fields", nullable = false, columnDefinition = "jsonb")
	private List<String> publicProfileFields = new ArrayList<>();

	/** Whether the public form shows a rating control (PRD 4.5). */
	@Column(name = "rating_enabled", nullable = false)
	private boolean ratingEnabled = false;

	/**
	 * Monotonic per-table sequence, used to rank a user's Spaces by creation
	 * order.
	 *
	 * <p>Read-only state after a downgrade is derived from this ranking
	 * (OPEN-B). {@code createdAt} alone is not sufficient: two Spaces can
	 * share the same microsecond, and PostgreSQL's sort is not stable, so the
	 * read-only set could differ between two otherwise identical requests.
	 */
	@Column(name = "seq", insertable = false, updatable = false)
	private Long seq;

	public AppUser getOwner() {
		return owner;
	}

	public void setOwner(AppUser owner) {
		this.owner = owner;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getThemeKey() {
		return themeKey;
	}

	public void setThemeKey(String themeKey) {
		this.themeKey = themeKey;
	}

	public Map<String, Object> getFormConfig() {
		return formConfig;
	}

	public void setFormConfig(Map<String, Object> formConfig) {
		this.formConfig = formConfig;
	}

	public Map<String, Object> getEmbedConfig() {
		return embedConfig;
	}

	public void setEmbedConfig(Map<String, Object> embedConfig) {
		this.embedConfig = embedConfig;
	}

	public List<String> getPublicProfileFields() {
		return publicProfileFields;
	}

	public void setPublicProfileFields(List<String> publicProfileFields) {
		this.publicProfileFields = publicProfileFields;
	}

	public boolean isRatingEnabled() {
		return ratingEnabled;
	}

	public void setRatingEnabled(boolean ratingEnabled) {
		this.ratingEnabled = ratingEnabled;
	}

	public Long getSeq() {
		return seq;
	}
}
