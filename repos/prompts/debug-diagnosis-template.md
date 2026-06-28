# Debug Diagnosis Prompt Template

Use this before asking AI to fix a bug.

---
Bug summary:
[Describe the symptom]

Expected behavior:
[What should happen]

Observed behavior:
[What actually happens]

Relevant docs:
- docs/module-[name].md
- docs/status-matrix.md
- docs/ai-working-rules.md

Instructions:
Do not fix immediately. First diagnose.

Return:
1. Exact code paths that can produce the observed result
2. Most likely root cause
3. Any conflicting status/outcome writes
4. Minimal fix options
5. Recommended regression tests
---
