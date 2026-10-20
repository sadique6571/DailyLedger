"""Normalize AVD memory keys before boot; emulator config expects integer MB."""
import os
from pathlib import Path

config = Path(os.environ["ANDROID_AVD_HOME"]) / "test.avd" / "config.ini"
settings = {}
for line in config.read_text().splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        settings[key.strip()] = value.strip()
# emulator-runner writes hw.heapSize, but Android reads vm.heapSize.
settings.pop("hw.heapSize", None)
settings.update({"hw.ramSize": "2048", "vm.heapSize": "256"})
config.write_text("\n".join(f"{key}={value}" for key, value in settings.items()) + "\n")
print("CI AVD: RAM 2048 MB, VM heap 256 MB")
