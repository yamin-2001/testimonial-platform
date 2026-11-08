import { apiFetch } from "./api";

/**
 * A Space's embed, as the owner configures it (PRD 4.8).
 *
 * Mirrors `EmbedDtos` and `EmbedConfiguration` on the Java side.
 */

/**
 * The four embed settings.
 *
 * The backend owns which of them are free (PRD 4.8, 7): the layout, the
 * mode, the background colour and the animation.
 */
export interface EmbedSettings {
  /** "masonry" or "carousel". Carousel needs Premium. */
  layout: EmbedLayout;
  /** "light" or "dark". Both are free. */
  mode: EmbedMode;
  /** A hex colour, or "" for the mode's own background. Premium. */
  backgroundColor: string;
  /** Whether the cards animate in. Premium. */
  animation: boolean;
}

export type EmbedLayout = "masonry" | "carousel";
export type EmbedMode = "light" | "dark";

/**
 * One submitter field, and whether the embed publishes it.
 */
export interface PublicProfileField {
  key: PublicProfileFieldKey;
  enabled: boolean;
}

export type PublicProfileFieldKey =
  | "companyName"
  | "jobTitle"
  | "websiteUrl"
  | "socialLinks";

/**
 * The fields the owner can choose to publish.
 *
 * The name, the text and the rating are always shown; the email is never
 * shown (PRD 4.8).
 */
export const PUBLIC_PROFILE_FIELDS: {
  key: PublicProfileFieldKey;
  label: string;
}[] = [
  { key: "companyName", label: "Company name" },
  { key: "jobTitle", label: "Job title" },
  { key: "websiteUrl", label: "Website URL" },
  { key: "socialLinks", label: "Social media links" },
];

/**
 * A Space's embed configuration.
 */
export interface EmbedConfiguration {
  settings: EmbedSettings;
  publicProfileFields: PublicProfileField[];
}

/**
 * The fields sent when saving a Space's embed configuration.
 *
 * Everything is optional: only what is sent is changed.
 */
export interface UpdateEmbedInput {
  layout?: EmbedLayout;
  mode?: EmbedMode;
  backgroundColor?: string;
  animation?: boolean;
  publicProfileFields?: PublicProfileFieldKey[];
}

/**
 * One testimonial, as the embed shows it.
 *
 * There is no email field, and there never will be: the embed must never
 * expose a submitter's email address (PRD 4.8).
 */
export interface EmbedTestimonial {
  id: string;
  name: string;
  message: string;
  rating: number | null;
  companyName: string | null;
  jobTitle: string | null;
  websiteUrl: string | null;
  socialLinks: string[];
}

/**
 * What an embed renders.
 *
 * Mirrors `EmbedContent` on the Java side.
 */
export interface EmbedContent {
  title: string;
  subtitle: string | null;
  settings: EmbedSettings;
  testimonials: EmbedTestimonial[];
}

/**
 * A Space's embed configuration.
 */
export function getEmbedConfiguration(spaceId: string): Promise<EmbedConfiguration> {
  return apiFetch<EmbedConfiguration>(`/api/spaces/${spaceId}/embed`);
}

/**
 * Saves a Space's embed configuration.
 *
 * Throws an `ApiError` whose `fieldErrors` carry the backend's per-field
 * messages, so the page can show them next to the control that caused them.
 */
export function updateEmbedConfiguration(
  spaceId: string,
  input: UpdateEmbedInput,
): Promise<EmbedConfiguration> {
  return apiFetch<EmbedConfiguration>(`/api/spaces/${spaceId}/embed`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
