import { apiFetch } from "./api";

/**
 * The signed-in user, as returned by `GET /api/auth/me`.
 */
export interface CurrentUser {
  id: string;
  name: string;
  email: string;
  emailVerified: boolean;
}

/**
 * The response returned after registering or signing in.
 */
export interface AuthResponse {
  user: CurrentUser;
  emailVerified: boolean;
}

export interface RegisterInput {
  name: string;
  email: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

/**
 * Registers a new account.
 *
 * The account is created unverified; a verification link is emailed.
 */
export function register(input: RegisterInput): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

/**
 * Signs in. The backend sets the session cookies in the response.
 */
export function login(input: LoginInput): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

/**
 * Signs out, revoking the session.
 */
export async function logout(): Promise<void> {
  await apiFetch<void>("/api/auth/logout", { method: "POST" });
}

/**
 * Returns the signed-in user.
 *
 * Throws an `ApiError` with status 401 when there is no valid session.
 */
export function currentUser(): Promise<CurrentUser> {
  return apiFetch<CurrentUser>("/api/auth/me");
}

/**
 * Confirms an email address using the token from the emailed link.
 */
export async function verifyEmail(token: string): Promise<void> {
  await apiFetch<void>(`/api/auth/verify-email?token=${encodeURIComponent(token)}`, {
    method: "POST",
  });
}

/**
 * Starts a password reset.
 *
 * The backend answers the same way for unknown addresses, so this cannot be
 * used to find out which addresses have accounts.
 */
export async function forgotPassword(email: string): Promise<void> {
  await apiFetch<void>("/api/auth/forgot-password", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

/**
 * Completes a password reset using the token from the emailed link.
 */
export async function resetPassword(token: string, password: string): Promise<void> {
  await apiFetch<void>(`/api/auth/reset-password?token=${encodeURIComponent(token)}`, {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

/**
 * The URL that starts a Google sign-in.
 *
 * `target` tells the backend where to send the user afterwards.
 */
export function googleSignInUrl(target?: string): string {
  const base = "/oauth2/authorization/google";
  return target ? `${base}?target=${encodeURIComponent(target)}` : base;
}
