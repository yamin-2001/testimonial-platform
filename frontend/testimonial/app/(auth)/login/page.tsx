import { Suspense } from "react";

import { LoginForm } from "./login-form";

/**
 * The sign-in page.
 *
 * The form reads the `target` query parameter, so it is wrapped in a
 * Suspense boundary.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
