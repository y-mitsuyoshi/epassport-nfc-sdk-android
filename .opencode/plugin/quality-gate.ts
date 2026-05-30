import type { Plugin } from "@opencode-ai/plugin"

/**
 * Quality Gate Plugin
 * Replicates the PreToolUse hook from .gemini/settings.json:
 * Before running gradlew commands, automatically execute compile/test/lint.
 */
export default (async ({ client }) => {
  return {
    "tool.execute.before": async (input, output) => {
      if (input.tool === "bash") {
        const args = output.args as { command?: string; description?: string }
        const command = args.command || ""

        // Skip if this is already a verify/compile/test/lint command
        const isVerifyCommand =
          command.includes("compileReleaseKotlin") ||
          command.includes("testDebugUnitTest") ||
          command.includes("lint")

        // Intercept gradlew commands (except verify itself)
        if (command.includes("./gradlew") && !isVerifyCommand) {
          try {
            // Attempt to run quality gate via client tool invocation.
            // If client.tool() is not available in your opencode version,
            // comment this block and run `opencode verify` manually instead.
            // @ts-ignore
            if (client && typeof client.tool === "function") {
              // @ts-ignore
              await client.tool("bash", {
                command: "./gradlew :sdk-nfc:compileReleaseKotlin :sdk-nfc:testDebugUnitTest :sdk-nfc:lint",
                description: "Quality gate: compile, test, lint"
              })
            } else {
              console.log("[Quality Gate] Please run `opencode verify` before executing gradle commands.")
            }
          } catch (e) {
            console.error("[Quality Gate] Pre-check failed:", e)
            throw new Error("Quality gate failed. Fix compile/test/lint errors before proceeding.")
          }
        }
      }
    }
  }
}) satisfies Plugin
