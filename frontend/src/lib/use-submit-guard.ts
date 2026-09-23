import { useRef } from "react";

/**
 * React's `disabled={submitting}` only takes effect on the next render, so a fast double-click
 * (or double-tap, or Enter-then-click) can fire a submit handler twice before the button actually
 * disables. For most forms that just means a duplicate row; for password changes it's worse - the
 * first request succeeds and changes the password, then the second request checks the OLD password
 * against the ALREADY-changed hash and fails, silently leaving the user locked out of whatever they
 * actually typed. `guard` is checked and set synchronously (a ref, not state) so the second call in
 * the same tick is dropped before it does anything.
 */
export function useSubmitGuard() {
  const ref = useRef(false);

  function guard<T>(fn: () => Promise<T>): Promise<T> | undefined {
    if (ref.current) return undefined;
    ref.current = true;
    return fn().finally(() => {
      ref.current = false;
    });
  }

  return guard;
}
