"""Choose the patch notes of a release: the tag's message, else the release pull request, else a generic line."""
import argparse
import re
from pathlib import Path

MAX_CHARS = 4000


def from_tag(message: str) -> str:
    """An annotated tag's message minus its first line (the title, e.g. "Near 1.8.5") and any signature."""
    message = re.split(r"^-----BEGIN (?:PGP|SSH) SIGNATURE-----", message, maxsplit=1, flags=re.M)[0]
    lines = message.strip().splitlines()
    return "\n".join(lines[1:]).strip()


def from_pull_request(body: str) -> str:
    """The release pull request's description without tool attribution or instructions for the maintainer."""
    kept = [line for line in body.replace("\r\n", "\n").splitlines()
            if "Generated with" not in line and not line.lstrip().lower().startswith("tras integrar")]
    return "\n".join(kept).strip()


def notes(version: str, tag_message: str = "", pr_body: str = "") -> str:
    text = from_tag(tag_message) or from_pull_request(pr_body) or f"Actualización de Near {version}."
    if len(text) > MAX_CHARS:
        text = text[:MAX_CHARS].rsplit("\n", 1)[0].rstrip() + "\n\n…"
    return text + "\n"


def read(path: Path | None) -> str:
    return path.read_text(encoding="utf-8") if path and path.exists() else ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("--tag-message", type=Path)
    parser.add_argument("--pr-body", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(notes(args.version, read(args.tag_message), read(args.pr_body)), encoding="utf-8")


if __name__ == "__main__":
    main()
