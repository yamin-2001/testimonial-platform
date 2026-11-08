package com.yamin.testimonial.testimonials;

import java.util.List;

import com.yamin.testimonial.testimonials.web.SubmissionDtos.SubmissionRequest;

/**
 * Builds a {@link SubmissionRequest} for a test.
 *
 * <p>The request is a record with eleven fields, of which a test usually
 * cares about two or three. This keeps each test to the one thing it is
 * about, rather than a wall of nulls.
 */
final class Submissions {

	private String name;
	private String email;
	private String companyName;
	private String jobTitle;
	private String websiteUrl;
	private List<String> socialLinks;
	private String message;
	private Integer rating;
	private Boolean consent;
	private String captchaToken;
	private String companyWebsite;

	private Submissions(String name, String email, String message) {
		this.name = name;
		this.email = email;
		this.message = message;
	}

	/**
	 * A submission with the three fixed fields and nothing else.
	 */
	static Submissions of(String name, String email, String message) {
		return new Submissions(name, email, message);
	}

	Submissions withConsent(boolean consent) {
		this.consent = consent;
		return this;
	}

	Submissions withCompanyName(String companyName) {
		this.companyName = companyName;
		return this;
	}

	Submissions withJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
		return this;
	}

	Submissions withWebsiteUrl(String websiteUrl) {
		this.websiteUrl = websiteUrl;
		return this;
	}

	Submissions withSocialLinks(List<String> socialLinks) {
		this.socialLinks = socialLinks;
		return this;
	}

	Submissions withRating(Integer rating) {
		this.rating = rating;
		return this;
	}

	Submissions withCaptchaToken(String captchaToken) {
		this.captchaToken = captchaToken;
		return this;
	}

	Submissions withHoneypot(String companyWebsite) {
		this.companyWebsite = companyWebsite;
		return this;
	}

	/**
	 * The request itself.
	 */
	SubmissionRequest build() {
		return new SubmissionRequest(name, email, companyName, jobTitle, websiteUrl, socialLinks,
				message, rating, consent, captchaToken, companyWebsite);
	}
}
