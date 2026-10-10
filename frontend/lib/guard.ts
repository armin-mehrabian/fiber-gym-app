// Server-side page guard: sends visitors to the stage they belong on (see lib/stage.ts).
import { redirect } from "next/navigation";
import { getMe } from "@/lib/api";
import { stageFor, type Stage } from "@/lib/stage";
import type { User } from "@/lib/types";

// Call at the top of a server page with the stage the page belongs to.
// Anyone at a different stage is redirected on the server, before the page renders.
export async function guardPage(pageStage: Stage): Promise<User | null> {
  const user = await getMe();
  const stage = stageFor(user);
  if (stage !== pageStage) {
    redirect(stage);
  }
  return user;
}
