package com.yamin.testimonial.testimonials;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.yamin.testimonial.common.AuditableEntity;
import com.yamin.testimonial.spaces.Space;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A testimonial submitted through a Space's public page.
 *
 * <p>Each testimonial belongs to exactly one Space (PRD 6.2). Submitters never
 * authenticate (PRD 6.4); the submitter's email is stored but never exposed
 * publicly -- the embed exposes name, text, rating and the enabled public
 * profile fields only (PRD 4.8).
 */
@Entity
@Table(name = "testimonials")
public class Testimonial extends AuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "space_id", nullable = false)
	private Space space;

	/** Always required: name and email are fixed default fields (PRD 4.3). */
	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "email", nullable = false)
	private String email;

	/** The testimonial text. Always present and required; max 2000 characters. */
	@Column(name = "message", nullable = false, columnDefinition = "text")
	private String message;

	/**
	 * Optional integer rating from 1 to 5.
	 *
	 * <p>Mapped as {@link Short} because the column is {@code smallint}; a
	 * plain {@code Integer} would expect an {@code integer} column and fail
	 * schema validation.
	 */
	@Column(name = "rating", columnDefinition = "smallint")
	private Short rating;

	@Column(name = "company_name")
	private String companyName;

	@Column(name = "job_title")
	private String jobTitle;

	@Column(name = "website_url")
	private String websiteUrl;

	/**
	 * Social media links supplied by the submitter.
	 *
	 * <p>Stored as JSONB rather than a PostgreSQL array: under Spring Boot 4,
	 * Hibernate resolves a {@code List<String>} to a JSON JDBC type, so a
	 * {@code text[]} column cannot be mapped.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "social_links", columnDefinition = "jsonb")
	private List<String> socialLinks = new ArrayList<>();

	/**
	 * Whether the submitter ticked the consent box.
	 *
	 * <p>Stored, but never restricts display or Wall of Love inclusion
	 * (PRD 4.3, 6.9). A submission with the box unchecked is accepted.
	 */
	@Column(name = "consent_given", nullable = false)
	private boolean consentGiven = false;

	/** Owner-set favourite. Favorites sort above non-favorites in the inbox (PRD 4.7). */
	@Column(name = "favorite", nullable = false)
	private boolean favorite = false;

	/** Owner-set Wall of Love inclusion (PRD 4.7). */
	@Column(name = "wall_of_love", nullable = false)
	private boolean wallOfLove = false;

	/**
	 * Whether the owner has hidden this testimonial.
	 *
	 * <p>{@code false} means visible, which is the default on submission
	 * (PRD 4.7). Hidden testimonials stay in the inbox but never appear
	 * publicly (PRD 4.6, 6.11).
	 */
	@Column(name = "hidden", nullable = false)
	private boolean hidden = false;

	/** Submitter's IP, used as the rate-limiting key (PRD 4.5). */
	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "submitter_ip", columnDefinition = "inet")
	private InetAddress submitterIp;

	@Column(name = "user_agent")
	private String userAgent;

	public Space getSpace() {
		return space;
	}

	public void setSpace(Space space) {
		this.space = space;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public void setWebsiteUrl(String websiteUrl) {
		this.websiteUrl = websiteUrl;
	}

	public List<String> getSocialLinks() {
		return socialLinks;
	}

	public void setSocialLinks(List<String> socialLinks) {
		this.socialLinks = socialLinks;
	}

	public boolean isConsentGiven() {
		return consentGiven;
	}

	public void setConsentGiven(boolean consentGiven) {
		this.consentGiven = consentGiven;
	}

	public boolean isFavorite() {
		return favorite;
	}

	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	public boolean isWallOfLove() {
		return wallOfLove;
	}

	public void setWallOfLove(boolean wallOfLove) {
		this.wallOfLove = wallOfLove;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public InetAddress getSubmitterIp() {
		return submitterIp;
	}

	public void setSubmitterIp(InetAddress submitterIp) {
		this.submitterIp = submitterIp;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}
}
