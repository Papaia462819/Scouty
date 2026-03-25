from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from common import ASSETS_DIR, BUILD_DIR, PIPELINE_DIR


def run_step(args: list[str]) -> None:
    subprocess.run(args, check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the full Scouty knowledge pack pipeline.")
    parser.add_argument("--skip-fetch", action="store_true", help="Skip fetching approved source payloads.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=BUILD_DIR,
        help="Directory used for intermediate pipeline outputs.",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=ASSETS_DIR,
        help="Directory where the generated pack assets are copied.",
    )
    args = parser.parse_args(argv)

    if not args.skip_fetch:
        run_step([sys.executable, str(PIPELINE_DIR / "fetch_sources.py")])

    run_step(
        [
            sys.executable,
            str(PIPELINE_DIR / "build_knowledge_pack.py"),
            "--output-dir",
            str(args.output_dir),
            "--assets-dir",
            str(args.assets_dir),
        ]
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
