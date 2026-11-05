/**
 * Base configuration for talking to the Java backend.
 *
 * The backend is the sole authority for authentication, plan limits and data
 * isolation (PRD 6, 8), so every request from the browser is sent there and
 * authenticated with the backend-issued session cookie.
 */

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
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
