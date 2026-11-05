TASK: Recon → Evaluate → Assemble → Verify → Report

You will execute these steps in order. Do not skip steps. Do not merge steps.

STEP 1 — RECON (30 minutes max)
Search GitHub for these query families and collect repo URLs:
- "JavaFX LLM chat OpenAI compatible" 
- "Java chat client Base URL API key"
- "Compose Desktop LLM client Java"
- "Quarkus JavaFX LLM"
For each hit, record: repo URL, last commit date, build tool (Maven/Gradle), JDK requirement, whether README mentions configurable Base URL, whether README mentions API Key field.

Do NOT clone yet. Produce a ranked shortlist of 3 repos.

STEP 2 — EVALUATE (1 hour max)
Clone the top-ranked repo (expected: LLMFX). Do this:
- Read README + build file (pom.xml or build.gradle).
- Locate the class/method that constructs the LLM request. Find where the Base URL and API Key are read from.
- Check if the UI has an input field for Base URL and API Key. If yes, where.
- Run the build command from the README. Record whether it succeeds or fails, and the exact error if it fails.

If the build succeeds and the UI shows Base URL/Key fields: PROCEED to Step 3.
If the build fails: diagnose (JDK version mismatch? missing dependency?). If fixable in ≤3 file edits, fix and re-run. If not, fall back to the next shortlist repo and repeat Step 2.

STEP 3 — ASSEMBLE (2 hours max)
Make the absolute minimum changes required for this test:
  1. Launch the app.
  2. Enter a real OpenAI-compatible Base URL and API Key.
  3. Send "Hello, respond with one word."
  4. Confirm a reply appears (streaming or non-streaming, either is acceptable).

Allowed changes:
- Parameterizing a hardcoded URL into a config field (if the repo hardcodes one).
- Adding a missing API Key input field bound to the request header (if absent).
- Changing a default model name.
NOT allowed:
- Adding conversation history persistence.
- Adding settings screens beyond URL + Key.
- Refactoring the open-source code.
- Introducing new dependencies unless strictly required for the API call.

STEP 4 — VERIFY (15 minutes)
Produce a verification log with these exact fields:
- Repo used (URL + commit hash)
- Build command that worked
- Run command that worked
- Screenshot or terminal transcript of the "Hello" exchange
- List of every file changed, with a one-line reason per file
- Any known limitation (e.g., "only non-streaming works", "model name hardcoded to gpt-4o-mini")

STEP 5 — REPORT (5 minutes)
Write a one-page RUN.md containing:
- Prerequisites (JDK version, any external binary)
- Build command
- Run command
- Base URL format expected (e.g., https://api.openai.com/v1)
- API Key header name (e.g., Authorization: Bearer ...)
- Model name field (if configurable)
- Known limitations

Do not write any other documentation. Do not produce architecture diagrams. Do not propose Apex integration steps. The prototype is the deliverable; RUN.md is its manual.