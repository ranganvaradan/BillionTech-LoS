# Cross-Module Change Prompt Template

Use this only when a task truly spans modules.

---
Change objective:
[Describe the business outcome]

Modules involved:
- [Module A]
- [Module B]
- [Frontend if needed]

Instructions:
- Start by listing all impacted services, modules, APIs, and screens.
- Separate contract changes from internal logic changes.
- Minimize blast radius.
- Preserve canonical orchestration flow.

Return:
1. Scope map
2. Proposed sequence of changes
3. Risks and rollback points
4. Validation plan
---
