package com.yamin.testimonial.embed;

import java.util.List;
import java.util.UUID;

/**
 * One testimonial, as the embed shows it (PRD 4.8).
 *
 * <p>The name, the text and the rating are always here. The company name, job
 * title, website and social links are here only when the Space publishes
 * those fields.
 *
 * <p>There is no email. It is stored with the testimonial, but the embed never
 * exposes it, and there is no field on this record that could.
 *
 * @param id the testimonial's id
 * @param name the submitter's name
 * @param message the testimonial text
 * @param rating the rating, or null when there is none
 * @param companyName the company, when the Space publishes it
 * @param jobTitle the job title, when the Space publishes it
 * @param websiteUrl the website, when the Space publishes it
 * @param socialLinks the social links, when the Space publishes them
 */
public record EmbedTestimonial(
		UUID id,
		String name,
		String message,
		Integer rating,
		String companyName,
		String jobTitle,
		String websiteUrl,
		List<String> socialLinks) {
}
