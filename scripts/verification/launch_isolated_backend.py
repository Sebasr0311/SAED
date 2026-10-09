"""Launch the SAED backend against the isolated Oracle fixture with a scrubbed environment.

Secrets are read from the recovery state's private file and passed only through the child
environment. Nothing secret is printed. Stdout/stderr go to a log file next to this script.
"""
import json
import os
import pathlib
import subprocess
import sys
import time

REPO = pathlib.Path(r"C:\Users\JUAN\Antigravity IDE\SAED")
STATE = REPO / "local" / "isolated-oracle" / sys.argv[1] / "private-state.json"
JAR = REPO / "backend" / "target" / "backend-1.0.0-SNAPSHOT.jar"
CONFIG = REPO / "backend" / "src" / "test" / "resources" / "application-isolated.yml"
JAVA_HOME = pathlib.Path.home() / ".jdks" / "ms-17.0.19"
LOG = REPO / "local" / "isolated-oracle" / sys.argv[1] / "isolated-backend.log"
WAIT_SECONDS = int(sys.argv[2]) if len(sys.argv) > 2 else 180

state = json.loads(STATE.read_text(encoding="utf-8"))
env = {
    "SYSTEMROOT": os.environ["SYSTEMROOT"],
    "TEMP": os.environ["TEMP"],
    "TMP": os.environ["TMP"],
    "JAVA_HOME": str(JAVA_HOME),
    "PATH": str(JAVA_HOME / "bin") + os.pathsep + os.path.join(os.environ["SYSTEMROOT"], "System32"),
    "SAED_ISOLATED_RUNTIME_PASSWORD": state["runtime_password"],
    "SAED_ISOLATED_JWT_SECRET": state["jwt_secret"],
}
args = [
    str(JAVA_HOME / "bin" / "java.exe"), "-jar", str(JAR),
    "--spring.config.location=file:" + CONFIG.as_posix(),
    "--spring.profiles.active=isolated",
]
with LOG.open("w", encoding="utf-8") as log:
    proc = subprocess.Popen(args, cwd=str(LOG.parent), env=env, stdout=log, stderr=subprocess.STDOUT)
    deadline = time.time() + WAIT_SECONDS
    outcome = "TIMEOUT"
    while time.time() < deadline:
        if proc.poll() is not None:
            outcome = f"EXITED code={proc.returncode}"
            break
        text = LOG.read_text(encoding="utf-8", errors="replace")
        if "Started " in text and " in " in text and "seconds" in text:
            outcome = "STARTED"
            break
        time.sleep(3)
    print(outcome, "pid=", proc.pid)
