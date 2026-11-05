# Next Steps: Agent Swarm & Tool Execution Pipeline

## Objective
With API connectivity and editable model IDs now functional, the next critical milestone is transforming the app from a standard chat interface into a functional "Agentic" platform. This involves implementing the **Agent Swarm** (custom sub-agents) and the **Tool Execution Pipeline** with security interlocks.

Do not worry about maxTokens or token limit handling in this phase.

## Step 1: Complete the Rebranding Cleanup
Before adding new features, eliminate all residual "Narrator" references in the networking and UI layers.

- **Action**: Open `AIGateway.kt`.
- **Task**: Search for and replace the following strings:
  - `"Apex Narrator AI Gateway [Offline / Unconfigured Mode]"` -> `"Apex Agent AI Gateway [Offline / Unconfigured Mode]"`
  - `"Apex Narrator Gateway Error"` -> `"Apex Agent Gateway Error"`
  - Any other user-facing log or error messages containing "Narrator".
- **Action**: Scan `strings.xml` and Compose UI files to ensure all labels reflect "Agent" rather than "Narrator".

## Step 2: Implement the Agent Swarm Registry
Based on the UI mockup (Screenshot 1), the user needs to create and manage specialized sub-agents. 

### 2.1 Data Layer
- Create a data class for the sub-agent configuration:
  ```kotlin
  data class AgentCard(
      val id: String = UUID.randomUUID().toString(),
      val name: String,
      val systemPrompt: String,
      val modelOverride: String? = null // Optional: overrides provider default
  )