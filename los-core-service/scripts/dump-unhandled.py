import subprocess
raw = subprocess.check_output(
    ["docker", "logs", "--since", "15m", "billiontechlos-core"],
    stderr=subprocess.STDOUT,
    text=True,
    errors="replace",
)
lines = raw.splitlines()
for i, line in enumerate(lines):
    if "Unhandled exception" in line:
        print("---")
        for j in range(i, min(i + 15, len(lines))):
            print(lines[j])
