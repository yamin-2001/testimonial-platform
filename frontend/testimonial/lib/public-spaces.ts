import { apiFetch, ApiError } from "./api";
import { FieldSettings } from "./spaces";

/**
 * A Space, as the public page sees it.
 *
 * Mirrors `SubmissionDtos.PublicSpaceResponse` on the Java side.
 *
 * The public Space carries only what a visitor needs. It has no id, no
 * owner and no counts, so a visitor cannot learn anything about the account
 * behind it (PRD 6.4).
 */
export interface PublicSpace {
  title: string;
  subtitle: string | null;
  slug: string;
  themeKey: string;
  ratingEnabled: boolean;
  formConfig: Record<string, FieldSettings>;
  /** Whether the Space is currently taking submissions (PRD 4.9). */
  accepting: boolean;
}

/**
 * The values a visitor submits.
 *
 * Mirrors `SubmissionDtos.SubmissionRequest` on the Java side.
 */
export interface SubmissionInput {
  name: string;
  email: string;
  companyName?: string;
  jobTitle?: string;
  websiteUrl?: string;
  socialLinks?: string[];
  message: string;
  rating?: number | null;
  consent?: boolean;
  /** The CAPTCHA token, when a CAPTCHA is configured. */
  captchaToken?: string;
  /**
   * The hidden field bots fill in.
   *
   * A human never sees it, so a value here means the submission is not from
   * a person.
   */
  companyWebsite?: string;
}

/**
 * The outcome of a submission.
 */
export interface SubmissionResult {
  id: string | null;
  received: boolean;
}

/**
 * The Space behind a public slug.
 *
 * Returns null when there is no Space with that slug, so the page can show
 * its not-found page. Other failures are left to the caller.
 */
export async function fetchPublicSpace(slug: string): Promise<PublicSpace | null> {
  try {
    return await apiFetch<PublicSpace>(
      `/api/public/spaces/${encodeURIComponent(slug)}`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/**
 * Sends a testimonial to a Space.
 *
 * Throws an {@link ApiError} whose `fieldErrors` carry the backend's
 * per-field messages, so the form can show them next to the inputs
 * (PRD 4.5).
 */
export function submitTestimonial(
  slug: string,
  input: SubmissionInput,
): Promise<SubmissionResult> {
  return apiFetch<SubmissionResult>(
    `/api/public/spaces/${encodeURIComponent(slug)}/testimonials`,
    {
      method: "POST",
      body: JSON.stringify(input),
    },
  );
}
