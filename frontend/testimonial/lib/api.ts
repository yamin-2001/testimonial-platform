/**
 * Base configuration for talking to the Java backend.
 *
 * The backend is the sole authority for authentication, plan limits and data
 * isolation (PRD 6, 8), so every request from the browser is sent there and
 * authenticated with the backend-issued session cookie.
 *
 * Requests go to a relative `/api/...` path rather than to the backend's host
 * directly: `next.config.ts` rewrites `/api/*` to the backend, so the browser
 * sees a single origin. That is what lets the session cookie stay
 * `SameSite=Lax` instead of needing `SameSite=None` (which would require
 * HTTPS on every request).
 */

/**
 * Base URL for backend calls.
 *
 * Empty by default, which makes every path relative to the current origin.
 */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

/**
 * The backend's absolute URL, used for server-side calls.
 *
 * A relative path is only meaningful in the browser. Node's `fetch` requires an
 * absolute URL, so server components and route handlers must call the backend
 * directly instead of going through the Next.js rewrite.
 */
export const SERVER_API_BASE_URL =
  process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * True when this code is running in the browser rather than on the server.
 */
const isBrowser = typeof window !== "undefined";

/**
 * Raised for non-2xx responses so callers can surface the backend's
 * field-level error messages (PRD 4.5: "Missing required fields return
 * field-level errors").
 */
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown) {
    super(ApiError.extractMessage(status, body));
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }

  private static extractMessage(status: number, body: unknown): string {
    if (body && typeof body === "object" && "message" in body) {
      const { message } = body as { message?: unknown };
      if (typeof message === "string" && message.length > 0) {
        return message;
      }
    }
    return `Request failed with status ${status}`;
  }

  /**
   * The per-field validation messages, keyed by field name.
   *
   * Empty when the failure was not a validation failure.
   */
  get fieldErrors(): Record<string, string> {
    if (this.body && typeof this.body === "object" && "errors" in this.body) {
      const { errors } = this.body as { errors?: unknown };
      if (errors && typeof errors === "object") {
        return errors as Record<string, string>;
      }
    }
    return {};
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const base = isBrowser ? API_BASE_URL : SERVER_API_BASE_URL;

  const response = await fetch(`${base}${path}`, {
    ...init,
    // Send the session cookie so Spring Security can identify the user.
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...init.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
