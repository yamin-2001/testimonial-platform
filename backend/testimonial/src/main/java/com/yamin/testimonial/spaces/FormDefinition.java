package com.yamin.testimonial.spaces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Space's public form actually collects (PRD 4.3).
 *
 * <p>The owner configures the optional fields, but the rules about them are
 * fixed: name, email and the testimonial text are always collected and always
 * required; the consent box is always shown and never required; and a field
 * that is switched off must not appear on the form at all.
 *
 * <p>This is the single place those rules live. The public page asks it what
 * to render, and the submission endpoint asks it what to accept, so a form
 * that shows a field and a form that accepts a field can never disagree.
 */
public final class FormDefinition {

	private FormDefinition() {
	}

	/**
	 * The fields the form collects, in the order they are shown.
	 */
	public static List<FormField> visibleFields(Map<String, Object> formConfig) {
		List<FormField> fields = new ArrayList<>();
		fields.add(FormField.NAME);
		fields.add(FormField.EMAIL);

		for (FormField field : FormField.configurableFields()) {
			if (isEnabled(formConfig, field)) {
				fields.add(field);
			}
		}

		fields.add(FormField.MESSAGE);
		return fields;
	}

	/**
	 * Whether a field must be filled in.
	 *
	 * <p>The fixed fields always must. An optional field must only when the
	 * owner switched it on and marked it required.
	 */
	public static boolean isRequired(Map<String, Object> formConfig, FormField field) {
		if (field.getRequirement() == FormField.Requirement.ALWAYS_REQUIRED) {
			return true;
		}
		if (!field.isConfigurable()) {
			return false;
		}
		return isEnabled(formConfig, field) && isRequiredInConfig(formConfig, field);
	}

	/**
	 * Whether a field is switched on.
	 *
	 * <p>A field that the configuration does not mention is on: the default is
	 * to collect everything optional.
	 */
	public static boolean isEnabled(Map<String, Object> formConfig, FormField field) {
		if (!field.isConfigurable()) {
			return true;
		}
		return enabledInConfig(formConfig, field);
	}

	/**
	 * The fields a submission may carry.
	 *
	 * <p>A field that is switched off is not accepted, even if the client
	 * sends it (PRD 4.3: "Disabled fields don't appear on the public form and
	 * are rejected if submitted").
	 */
	public static List<FormField> acceptedFields(Map<String, Object> formConfig) {
		List<FormField> accepted = new ArrayList<>(visibleFields(formConfig));
		// The consent box is always shown, so it is always accepted too.
		accepted.add(FormField.CONSENT);
		return accepted;
	}

	/**
	 * Whether a submission may carry a value for a field.
	 */
	public static boolean accepts(Map<String, Object> formConfig, FormField field) {
		return acceptedFields(formConfig).contains(field);
	}

	/**
	 * The configuration, as the API presents it.
	 *
	 * <p>Only the configurable fields, each reduced to an enabled and a
	 * required flag.
	 *
	 * <p>The flags are kept in a linked map so that the JSON always comes out
	 * in the same order. An immutable {@code Map.of} has no defined order, so
	 * the same Space would serialise differently from one request to the
	 * next.
	 */
	public static Map<String, Object> describe(Map<String, Object> formConfig) {
		Map<String, Object> described = new LinkedHashMap<>();
		for (FormField field : FormField.configurableFields()) {
			Map<String, Object> settings = new LinkedHashMap<>();
			settings.put("enabled", isEnabled(formConfig, field));
			settings.put("required", isRequired(formConfig, field));
			described.put(field.getKey(), settings);
		}
		return described;
	}

	/**
	 * Whether the field's settings say it is switched on.
	 */
	private static boolean enabledInConfig(Map<String, Object> formConfig, FormField field) {
		Map<?, ?> settings = settingsOf(formConfig, field);
		if (settings == null) {
			return true;
		}
		Object enabled = settings.get("enabled");
		return !(enabled instanceof Boolean value) || value;
	}

	/**
	 * Whether the field's settings say it is required.
	 */
	private static boolean isRequiredInConfig(Map<String, Object> formConfig, FormField field) {
		Map<?, ?> settings = settingsOf(formConfig, field);
		if (settings == null) {
			return false;
		}
		Object required = settings.get("required");
		return required instanceof Boolean value && value;
	}

	/**
	 * The field's settings, if the configuration has any.
	 */
	private static Map<?, ?> settingsOf(Map<String, Object> formConfig, FormField field) {
		if (formConfig == null) {
			return null;
		}
		Object raw = formConfig.get(field.getKey());
		return raw instanceof Map<?, ?> settings ? settings : null;
	}
}
