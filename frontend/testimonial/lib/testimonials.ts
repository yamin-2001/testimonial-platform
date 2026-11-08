import { apiFetch } from "./api";

/**
 * A testimonial, as returned by the backend.
 *
 * Mirrors `InboxDtos.TestimonialResponse` on the Java side.
 */
export interface Testimonial {
  id: string;
  spaceId: string;
  name: string;
  email: string;
  message: string;
  rating: number | null;
  companyName: string | null;
  jobTitle: string | null;
  websiteUrl: string | null;
  socialLinks: string[];
  consentGiven: boolean;
  favorite: boolean;
  wallOfLove: boolean;
  hidden: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * One page of the inbox (PRD 4.7).
 */
export interface InboxPage {
  content: Testimonial[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * The filters the inbox can be narrowed by (PRD 4.7).
 *
 * A filter that is `undefined` is not applied.
 */
export interface InboxFilters {
  search?: string;
  favorite?: boolean;
  wallOfLove?: boolean;
  hidden?: boolean;
  rating?: number;
  page?: number;
  size?: number;
}

/**
 * The fields sent when editing a testimonial.
 *
 * Only what is sent is changed, so an edit of the text leaves the rest alone.
 */
export interface UpdateTestimonialInput {
  name?: string;
  email?: string;
  message?: string;
  rating?: number;
}

/**
 * The Space's inbox, filtered and searched.
 */
export function listInbox(
  spaceId: string,
  filters: InboxFilters = {},
): Promise<InboxPage> {
  const query = new URLSearchParams();

  if (filters.search) {
    query.set("search", filters.search);
  }
  if (filters.favorite !== undefined) {
    query.set("favorite", String(filters.favorite));
  }
  if (filters.wallOfLove !== undefined) {
    query.set("wallOfLove", String(filters.wallOfLove));
  }
  if (filters.hidden !== undefined) {
    query.set("hidden", String(filters.hidden));
  }
  if (filters.rating !== undefined) {
    query.set("rating", String(filters.rating));
  }
  if (filters.page !== undefined) {
    query.set("page", String(filters.page));
  }
  if (filters.size !== undefined) {
    query.set("size", String(filters.size));
  }

  const suffix = query.toString();
  return apiFetch<InboxPage>(
    `/api/spaces/${spaceId}/testimonials${suffix ? `?${suffix}` : ""}`,
  );
}

/**
 * Marks a testimonial as a favourite, or unmarks it.
 */
export function setFavorite(
  spaceId: string,
  testimonialId: string,
  value: boolean,
): Promise<Testimonial> {
  return apiFetch<Testimonial>(
    `/api/spaces/${spaceId}/testimonials/${testimonialId}/favorite`,
    { method: "PUT", body: JSON.stringify({ value }) },
  );
}

/**
 * Adds a testimonial to the Wall of Love, or takes it off.
 */
export function setWallOfLove(
  spaceId: string,
  testimonialId: string,
  value: boolean,
): Promise<Testimonial> {
  return apiFetch<Testimonial>(
    `/api/spaces/${spaceId}/testimonials/${testimonialId}/wall-of-love`,
    { method: "PUT", body: JSON.stringify({ value }) },
  );
}

/**
 * Hides a testimonial from the public pages, or shows it again.
 */
export function setHidden(
  spaceId: string,
  testimonialId: string,
  value: boolean,
): Promise<Testimonial> {
  return apiFetch<Testimonial>(
    `/api/spaces/${spaceId}/testimonials/${testimonialId}/hidden`,
    { method: "PUT", body: JSON.stringify({ value }) },
  );
}

/**
 * Edits a testimonial's details.
 */
export function updateTestimonial(
  spaceId: string,
  testimonialId: string,
  input: UpdateTestimonialInput,
): Promise<Testimonial> {
  return apiFetch<Testimonial>(
    `/api/spaces/${spaceId}/testimonials/${testimonialId}`,
    { method: "PATCH", body: JSON.stringify(input) },
  );
}

/**
 * Deletes a testimonial for good.
 */
export function deleteTestimonial(
  spaceId: string,
  testimonialId: string,
): Promise<void> {
  return apiFetch<void>(
    `/api/spaces/${spaceId}/testimonials/${testimonialId}`,
    { method: "DELETE" },
  );
}
