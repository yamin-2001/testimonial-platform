import { Suspense } from "react";

import { ResetPasswordForm } from "./reset-password-form";

/**
 * The password reset page.
 *
 * The form reads the token from the query string, so it is wrapped in a
 * Suspense boundary.
 */
export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordForm />
    </Suspense>
  );
}
