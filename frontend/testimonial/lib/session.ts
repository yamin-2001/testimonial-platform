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
 * Reads the signed-in user on the server.
 *
 * Returns `null` when there is no valid session, so a caller can decide
 * whether to redirect or to render a signed-out state. The request is made
 * server-side with the browser's cookie attached, which keeps the token out
 * of the page and out of any client bundle.
 */
export async function getCurrentUser(): Promise<CurrentUser | null> {
  const cookieStore = await cookies();
  const token = cookieStore.get(ACCESS_TOKEN_COOKIE);
  if (!token) {
    return null;
  }

  try {
    // The backend call happens on the server, where the browser's cookie is
    // not attached automatically, so it is forwarded explicitly.
    return await apiFetch<CurrentUser>("/api/auth/me", {
      headers: { Cookie: `${ACCESS_TOKEN_COOKIE}=${token.value}` },
    });
  } catch (error) {
    // A missing or expired session is an ordinary state, not a failure: the
    // access token is short-lived and the client refreshes it separately.
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return null;
    }
    throw error;
  }
}
