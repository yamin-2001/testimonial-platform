package com.yamin.testimonial.spaces;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The fields the public form can collect (PRD 4.3).
 *
 * <p>Two fields are fixed and always required: name and email. They cannot be
 * disabled or made optional, because a testimonial without them cannot be
 * attributed or contacted.
 *
 * <p>Four fields are optional and each can be enabled or disabled, and made
 * required or optional. Testimonial text is always present and required, and
 * the consent checkbox is always shown but always optional -- its value is
 * stored and never gates display.
 */
public enum FormField {

	/** Always required. Cannot be disabled. */
	NAME("name", "Name", Requirement.ALWAYS_REQUIRED),

	/** Always required. Cannot be disabled. */
	EMAIL("email", "Email", Requirement.ALWAYS_REQUIRED),

	/** The testimonial itself. Always present and required. */
	MESSAGE("message", "Testimonial", Requirement.ALWAYS_REQUIRED),

	/** The consent checkbox. Always shown, never required. */
	CONSENT("consent", "Consent", Requirement.ALWAYS_OPTIONAL),

	/** Optional. */
	COMPANY_NAME("companyName", "Company name", Requirement.OPTIONAL),

	/** Optional. */
	JOB_TITLE("jobTitle", "Job title", Requirement.OPTIONAL),

	/** Optional. */
	WEBSITE_URL("websiteUrl", "Website URL", Requirement.OPTIONAL),

	/** Optional. Accepts several links. */
	SOCIAL_LINKS("socialLinks", "Social media links", Requirement.OPTIONAL);

	/** How a field's required-ness is decided. */
	public enum Requirement {
		/** The field is always collected and always required. */
		ALWAYS_REQUIRED,

		/** The field is always shown, but never required. */
		ALWAYS_OPTIONAL,

		/** The field may be enabled, and when enabled may be required. */
		OPTIONAL
	}

	private final String key;

	private final String label;

	private final Requirement requirement;

	FormField(String key, String label, Requirement requirement) {
		this.key = key;
		this.label = label;
		this.requirement = requirement;
	}

	/** The key used in {@code form_config}. */
	public String getKey() {
		return key;
	}

	/** The name shown to the user. */
	public String getLabel() {
		return label;
	}

	/** How the field's required-ness is decided. */
	public Requirement getRequirement() {
		return requirement;
	}

	/** Whether the user may turn this field on and off. */
	public boolean isConfigurable() {
		return requirement == Requirement.OPTIONAL;
	}

	/**
	 * Whether the field is always collected, whatever the configuration says.
	 */
	public boolean isAlwaysPresent() {
		return requirement == Requirement.ALWAYS_REQUIRED
				|| requirement == Requirement.ALWAYS_OPTIONAL;
	}

	/**
	 * The fields the user may configure.
	 */
	public static List<FormField> configurableFields() {
		return List.of(COMPANY_NAME, JOB_TITLE, WEBSITE_URL, SOCIAL_LINKS);
	}

	/**
	 * Looks a field up by its key.
	 */
	public static Optional<FormField> byKey(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (FormField field : values()) {
			if (field.key.equals(key)) {
				return Optional.of(field);
			}
		}
		return Optional.empty();
	}

	/**
	 * Builds the default configuration.
	 *
	 * <p>Every optional field starts enabled and optional, so a new Space
	 * collects a complete testimonial without the owner configuring anything.
	 */
	public static Map<String, Object> defaultConfig() {
		Map<String, Object> config = new LinkedHashMap<>();
		for (FormField field : values()) {
			if (field.isConfigurable()) {
				config.put(field.getKey(), Map.of("enabled", true, "required", false));
			}
		}
		return config;
	}
}
