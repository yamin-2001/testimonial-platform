package com.yamin.testimonial.embed;

import java.util.List;
import java.util.Optional;

import com.yamin.testimonial.spaces.FormField;

/**
 * The submitter fields an embed may expose (PRD 4.8).
 *
 * <p>The embed shows a testimonial's name, text and rating, plus whatever
 * public profile fields the Space's owner has enabled. The email is never one
 * of them: it is stored, but it never leaves the owner's inbox.
 *
 * <p>These are the same fields the public form can collect, and the two
 * questions are independent. A Space may collect a job title without choosing
 * to publish it, and may publish a job title it never asked for.
 */
public enum PublicProfileField {

	/** The submitter's company. */
	COMPANY_NAME("companyName", FormField.COMPANY_NAME),

	/** The submitter's job title. */
	JOB_TITLE("jobTitle", FormField.JOB_TITLE),

	/** The submitter's website. */
	WEBSITE_URL("websiteUrl", FormField.WEBSITE_URL),

	/** The submitter's social media links. */
	SOCIAL_LINKS("socialLinks", FormField.SOCIAL_LINKS);

	private final String key;

	private final FormField formField;

	PublicProfileField(String key, FormField formField) {
		this.key = key;
		this.formField = formField;
	}

	/** The value stored in {@code spaces.public_profile_fields}. */
	public String getKey() {
		return key;
	}

	/** The form field this exposes. */
	public FormField getFormField() {
		return formField;
	}

	/**
	 * Looks a field up by its stored key.
	 */
	public static Optional<PublicProfileField> byKey(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (PublicProfileField field : values()) {
			if (field.key.equals(key)) {
				return Optional.of(field);
			}
		}
		return Optional.empty();
	}

	/**
	 * Every field.
	 */
	public static List<PublicProfileField> all() {
		return List.of(values());
	}
}
