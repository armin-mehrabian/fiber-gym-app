// Every visitor belongs on exactly one of three "stages", in this order:
// not logged in → /login, logged in but not set up → /onboarding, set up → the app ("/").
// Pure on purpose: the login form (browser) and the page guards (server) both use it.
import type { User } from "@/lib/types";

export type Stage = "/login" | "/onboarding" | "/";

// Where this visitor should be.
export function stageFor(user: User | null): Stage {
  if (!user) {
    return "/login";
  }
  return user.onboarded ? "/" : "/onboarding";
}
