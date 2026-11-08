import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * The access-token cookie name. Must match `app.auth.access-token-cookie`
 * in the backend configuration.
 */
const ACCESS_TOKEN_COOKIE = "session";

/**
 * Routes that require a signed-in user.
 */
const PROTECTED_PREFIXES = ["/dashboard", "/spaces", "/inbox", "/billing", "/settings"];

/**
 * A first-pass check that redirects signed-out visitors away from protected
 * pages.
 *
 * <p>This only looks at the presence of the cookie. It does not validate the
 * token, because the backend is the authority on that and the check belongs
 * in the render path. The dashboard layout performs the authoritative check,
 * so a forged cookie gets past this file but no further.
 *
 * <p>Next.js 16 renamed the `middleware` convention to `proxy`.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  const isProtected = PROTECTED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
  if (!isProtected) {
    return NextResponse.next();
  }

  const hasSession = request.cookies.has(ACCESS_TOKEN_COOKIE);
  if (hasSession) {
    return NextResponse.next();
  }

  // Remember where they were going so sign-in can return them there.
  const target = `${pathname}${search}`;
  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("target", target);

  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/spaces/:path*",
    "/inbox/:path*",
    "/billing/:path*",
    "/settings/:path*",
  ],
};
