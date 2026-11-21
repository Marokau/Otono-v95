"""Build update assets from two reviewed publication tags; run on Windows with Java 21."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build(base, version, output):
    for tag in (base, version):
        if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
            raise ValueError("Use release tags such as v0.2.1.")
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        tmp = Path(directory)
        for tag in (base, version):
            subprocess.run(["git", "archive", "--format=zip", "-o", str(tmp / (tag + ".zip")),
                            "refs/tags/" + tag], check=True)
        with zipfile.ZipFile(tmp / (base + ".zip")) as old, zipfile.ZipFile(tmp / (version + ".zip")) as new:
            if new.read("launcher/release-version.txt").decode().strip() != version:
                raise ValueError("The target tag and launcher version differ.")
            target = tmp / "target-release"
            new.extractall(target)  # Input is the reviewed local publication tag.
            subprocess.run([str(target / "server/mvnw.cmd"), "-B", "-ntp", "-DskipTests", "package"],
                           cwd=target / "server", check=True, shell=False)
            patch = tmp / f"Otono-v95-{base}-to-{version}.zip"
            bootstrap = tmp / "Otono-v95-Updater.zip"
            old_names = {e.filename for e in old.infolist() if not e.is_dir()}
            new_names = {e.filename for e in new.infolist() if not e.is_dir()}
            files = []
            with zipfile.ZipFile(patch, "w", zipfile.ZIP_DEFLATED, compresslevel=5) as out:
                def add(path, before, after):
                    text = before is not None and b"\0" not in before
                    if text:
                        try:
                            before.decode("utf-8")
                        except UnicodeDecodeError:
                            text = False
                    files.append(dict(path=path, before=sha(before.replace(b"\r\n", b"\n") if text else before)
                                      if before is not None else None, after=sha(after) if after is not None else None, text=text))
                    if after is not None:
                        out.writestr("files/" + path, after)
                for path in sorted(old_names | new_names):
                    if path == "config.yaml" or path.startswith("game/"):
                        continue
                    before = old.read(path) if path in old_names else None
                    after = new.read(path) if path in new_names else None
                    if before != after:
                        add(path, before, after)
                add("server/target/server.jar", None, (target / "server/target/server.jar").read_bytes())
                out.writestr("patch.json", json.dumps(dict(format=1, base=base, version=version, files=files)))
            with zipfile.ZipFile(bootstrap, "w", zipfile.ZIP_DEFLATED) as out:
                for name in ("launcher/Update-Otono.ps1", "launcher/Check-for-Updates.cmd"):
                    out.writestr(name, new.read(name))
            for file in (patch, bootstrap):
                (output / file.name).write_bytes(file.read_bytes())
                print(file.name, sha(file.read_bytes()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base")
    parser.add_argument("version")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.base, args.version, args.output)
