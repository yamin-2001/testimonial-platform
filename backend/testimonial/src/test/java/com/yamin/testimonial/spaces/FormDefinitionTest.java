package com.yamin.testimonial.spaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules about what a Space's form collects (PRD 4.3).
 *
 * <p>These are the rules the public page renders by and the submission
 * endpoint accepts by, so they are pinned here rather than left to whichever
 * caller happens to read the configuration.
 */
class FormDefinitionTest {

	@Test
	@DisplayName("the form always collects name, email and the testimonial")
	void fixedFieldsAreAlwaysVisible() {
		List<FormField> fields = FormDefinition.visibleFields(Map.of());

		assertThat(fields).contains(FormField.NAME, FormField.EMAIL, FormField.MESSAGE);
	}

	@Test
	@DisplayName("the fixed fields are always required")
	void fixedFieldsAreAlwaysRequired() {
		Map<String, Object> config = Map.of();

		assertThat(FormDefinition.isRequired(config, FormField.NAME)).isTrue();
		assertThat(FormDefinition.isRequired(config, FormField.EMAIL)).isTrue();
		assertThat(FormDefinition.isRequired(config, FormField.MESSAGE)).isTrue();
	}

	@Test
	@DisplayName("the consent box is always shown and never required")
	void consentIsAlwaysShownAndNeverRequired() {
		Map<String, Object> config = Map.of();

		assertThat(FormDefinition.isEnabled(config, FormField.CONSENT)).isTrue();
		assertThat(FormDefinition.isRequired(config, FormField.CONSENT)).isFalse();
	}

	@Test
	@DisplayName("an optional field that is switched off is not on the form")
	void disabledFieldIsNotVisible() {
		Map<String, Object> config = Map.of(
				"companyName", Map.of("enabled", false, "required", false));

		assertThat(FormDefinition.visibleFields(config))
				.doesNotContain(FormField.COMPANY_NAME);
	}

	@Test
	@DisplayName("an optional field that is switched on is on the form")
	void enabledFieldIsVisible() {
		Map<String, Object> config = Map.of(
				"companyName", Map.of("enabled", true, "required", false));

		assertThat(FormDefinition.visibleFields(config))
				.contains(FormField.COMPANY_NAME);
	}

	@Test
	@DisplayName("a field that is switched off is rejected if it is submitted")
	void disabledFieldIsNotAccepted() {
		Map<String, Object> config = Map.of(
				"companyName", Map.of("enabled", false, "required", false));

		assertThat(FormDefinition.accepts(config, FormField.COMPANY_NAME)).isFalse();
		assertThat(FormDefinition.accepts(config, FormField.JOB_TITLE)).isTrue();
		assertThat(FormDefinition.accepts(config, FormField.NAME)).isTrue();
		assertThat(FormDefinition.accepts(config, FormField.CONSENT)).isTrue();
	}

	@Test
	@DisplayName("a field that is switched off cannot be required")
	void disabledFieldIsNeverRequired() {
		Map<String, Object> config = Map.of(
				"companyName", Map.of("enabled", false, "required", true));

		assertThat(FormDefinition.isRequired(config, FormField.COMPANY_NAME)).isFalse();
	}

	@Test
	@DisplayName("an enabled field can be required")
	void enabledFieldCanBeRequired() {
		Map<String, Object> config = Map.of(
				"companyName", Map.of("enabled", true, "required", true));

		assertThat(FormDefinition.isRequired(config, FormField.COMPANY_NAME)).isTrue();
	}

	@Test
	@DisplayName("a field the configuration does not mention is collected")
	void missingConfigurationDefaultsToOn() {
		assertThat(FormDefinition.isEnabled(Map.of(), FormField.COMPANY_NAME)).isTrue();
		assertThat(FormDefinition.isRequired(Map.of(), FormField.COMPANY_NAME)).isFalse();
	}

	@Test
	@DisplayName("the form collects the optional fields before the testimonial")
	void optionalFieldsComeBeforeTheTestimonial() {
		List<FormField> fields = FormDefinition.visibleFields(Map.of());

		assertThat(fields.indexOf(FormField.EMAIL))
				.isLessThan(fields.indexOf(FormField.COMPANY_NAME));
		assertThat(fields.indexOf(FormField.COMPANY_NAME))
				.isLessThan(fields.indexOf(FormField.MESSAGE));
	}

	@Test
	@DisplayName("the described configuration holds only the configurable fields")
	void descriptionHoldsOnlyConfigurableFields() {
		Map<String, Object> described = FormDefinition.describe(
				Map.of("companyName", Map.of("enabled", false, "required", true)));

		assertThat(described).containsOnlyKeys("companyName", "jobTitle",
				"websiteUrl", "socialLinks");
		assertThat(described.get("companyName"))
				.isEqualTo(Map.of("enabled", false, "required", false));
	}

	@Test
	@DisplayName("the described configuration comes out in a stable order")
	void descriptionIsOrdered() {
		// The same Space must serialise the same way every time, so the
		// flags are written in a fixed order rather than whatever an
		// immutable map happens to iterate in.
		Map<String, Object> described = FormDefinition.describe(Map.of());

		assertThat(described.keySet())
				.containsExactly("companyName", "jobTitle", "websiteUrl", "socialLinks");

		for (String key : described.keySet()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> settings = (Map<String, Object>) described.get(key);
			assertThat(settings.keySet()).containsExactly("enabled", "required");
		}
	}
}
