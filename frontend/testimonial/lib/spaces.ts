import { apiFetch } from "./api";

/**
 * A Space, as returned by the backend.
 *
 * Mirrors `SpaceDtos.SpaceResponse` on the Java side.
 */
export interface Space {
  id: string;
  title: string;
  subtitle: string | null;
  slug: string;
  themeKey: string;
  ratingEnabled: boolean;
  formConfig: Record<string, FieldSettings>;
  readOnly: boolean;
  testimonialCount: number;
  publicUrl: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Whether one optional field is collected, and whether it must be filled in.
 */
export interface FieldSettings {
  enabled: boolean;
  required: boolean;
}

/**
 * The optional fields a Space can collect.
 *
 * Name, email and the testimonial text are always collected and are not
 * configurable (PRD 4.3), so they are absent from this list.
 */
export const OPTIONAL_FIELDS = [
  { key: "companyName", label: "Company name" },
  { key: "jobTitle", label: "Job title" },
  { key: "websiteUrl", label: "Website URL" },
  { key: "socialLinks", label: "Social media links" },
] as const;

export type OptionalFieldKey = (typeof OPTIONAL_FIELDS)[number]["key"];

/**
 * A theme in the picker.
 */
export interface Theme {
  key: string;
  label: string;
  premium: boolean;
}

/**
 * The dashboard's headline numbers (PRD 4.6).
 */
export interface DashboardSummary {
  totalSpaces: number;
  totalTestimonials: number;
  uniqueSubmitters: number;
  spaceLimit: number;
  plan: "FREE" | "PREMIUM";
  premium: boolean;
}

/**
 * The fields sent when creating a Space.
 */
export interface CreateSpaceInput {
  title: string;
  subtitle?: string;
  slug?: string;
  themeKey?: string;
  ratingEnabled?: boolean;
  formConfig?: Record<string, FieldSettings>;
}

/**
 * The fields sent when updating a Space.
 *
 * Only what is sent is changed, so every field is optional.
 */
export interface UpdateSpaceInput {
  title?: string;
  subtitle?: string;
  slug?: string;
  themeKey?: string;
  ratingEnabled?: boolean;
  formConfig?: Record<string, FieldSettings>;
}

/**
 * The user's Spaces, oldest first.
 */
export function listSpaces(): Promise<Space[]> {
  return apiFetch<Space[]>("/api/spaces");
}

/**
 * One of the user's Spaces.
 */
export function getSpace(spaceId: string): Promise<Space> {
  return apiFetch<Space>(`/api/spaces/${spaceId}`);
}

/**
 * Creates a Space.
 */
export function createSpace(input: CreateSpaceInput): Promise<Space> {
  return apiFetch<Space>("/api/spaces", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

/**
 * Updates a Space.
 */
export function updateSpace(
  spaceId: string,
  input: UpdateSpaceInput,
): Promise<Space> {
  return apiFetch<Space>(`/api/spaces/${spaceId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

/**
 * Deletes a Space and everything in it.
 */
export function deleteSpace(spaceId: string): Promise<void> {
  return apiFetch<void>(`/api/spaces/${spaceId}`, { method: "DELETE" });
}

/**
 * The themes available to the signed-in user.
 *
 * Premium themes are listed but flagged, so the UI can show them locked with
 * an upgrade prompt (PRD 4.4).
 */
export function listThemes(): Promise<Theme[]> {
  return apiFetch<{ themes: Theme[] }>("/api/spaces/themes").then(
    (body) => body.themes,
  );
}

/**
 * The dashboard's headline numbers.
 */
export function getDashboardSummary(): Promise<DashboardSummary> {
  return apiFetch<DashboardSummary>("/api/spaces/dashboard/summary");
}

/**
 * The slug the backend would generate for a title.
 *
 * The backend owns the rule, so the preview is a convenience: the value the
 * user sends is still the one that counts. The slug is only locked once the
 * Space has received its first submission (PRD 4.2), so the user can always
 * change it before that.
 */
export function slugify(title: string): string {
  return title
    .normalize("NFD")
    .replace(/[̀-ͯ]+/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 120);
}
