import { cookies } from "next/headers";

import { ApiError, apiFetch } from "./api";
import { CurrentUser } from "./auth";

/**
 * The name of the cookie carrying the access token.
 *
 * Must match `app.auth.access-token-cookie` in the backend configuration.
 */
export const ACCESS_TOKEN_COOKIE = "session";

/**
 * The headers that carry the browser's session to the backend.
 *
 * The call happens on the server, where the browser's cookie is not attached
 * automatically, so it is forwarded explicitly.
 */
async function sessionHeaders(): Promise<Record<string, string> | null> {
  const cookieStore = await cookies();
  const token = cookieStore.get(ACCESS_TOKEN_COOKIE);
  if (!token) {
    return null;
  }
  return { Cookie: `${ACCESS_TOKEN_COOKIE}=${token.value}` };
}

/**
 * Reads the signed-in user on the server.
 *
 * Returns `null` when there is no valid session, so a caller can decide
 * whether to redirect or to render a signed-out state. The request is made
 * server-side with the browser's cookie attached, which keeps the token out
 * of the page and out of any client bundle.
 */
export async function getCurrentUser(): Promise<CurrentUser | null> {
  const headers = await sessionHeaders();
  if (!headers) {
    return null;
  }

  try {
    return await apiFetch<CurrentUser>("/api/auth/me", { headers });
  } catch (error) {
    // A missing or expired session is an ordinary state, not a failure: the
    // access token is short-lived and the client refreshes it separately.
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return null;
    }
    throw error;
  }
}

/**
 * Fetches data from the backend on the server, with the session attached.
 *
 * Returns `null` when there is no session, so a caller can tell "signed out"
 * apart from "signed in, but the data is empty". A `404` also becomes `null`
 * rather than an error, because a Space that is not the signed-in user's is
 * simply not there (PRD 4.2).
 */
export async function apiFetchWithSession<T>(path: string): Promise<T | null> {
  const headers = await sessionHeaders();
  if (!headers) {
    return null;
  }

  try {
    return await apiFetch<T>(path, { headers });
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403 || error.status === 404)) {
      return null;
    }
    throw error;
  }
}
