#!/usr/bin/env python3
"""Import CC0 library recordings as quiet, seamless offline PCM loops. Requires ffmpeg."""

import argparse
import array
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request

SAMPLE_RATE = 22_050
SOURCES = {
    "white": ("https://cdn.freesound.org/previews/165/165058_947433-hq.mp3", "5aca5a2faa33784956d489816cd2f82731e751da890c4283436c7e31ae988927"),
    "pink": ("https://cdn.freesound.org/previews/28/28639_181941-hq.mp3", "73513db7631a68a2804ca33b69f9ac220e8faeda10906a95f4f8e4a8196c7497"),
    "brown": ("https://cdn.freesound.org/previews/365/365932_5857547-hq.mp3", "fe0789085e5292533217431e3f413580421aeb3efe283c5a99fc14ec2909b790"),
    "rain": ("https://cdn.freesound.org/previews/501/501243_8644110-hq.mp3", "5fffe1bb4acdd51270b0fa84614be3fa354155b08f60881f4fe12fd0ff53f423"),
    "waves": ("https://cdn.freesound.org/previews/635/635917_2247456-hq.mp3", "e11d9a8d01ce9cfac82b2eb8bb75be4b4ce7a13b0bc03ec881115dc84118f553"),
}


def import_loop(source: Path, target: Path) -> dict:
    # Skip the first two seconds. Avoid sharp high frequencies without changing playback speed.
    decoded = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(source), "-ss", "2", "-t", "46",
        "-af", "highpass=f=25,lowpass=f=5000", "-ar", str(SAMPLE_RATE),
        "-ac", "1", "-f", "f32le", "-",
    ])
    samples = array.array("f")
    samples.frombytes(decoded)
    if sys.byteorder != "little":
        samples.byteswap()
    overlap = SAMPLE_RATE * 2
    if len(samples) < overlap * 3:
        raise ValueError(f"Source is too short: {source.name}")
    # Crossfade the tail into the head. The end then joins the tail's original preceding sample.
    loop = array.array("f", samples[:-overlap])
    for i in range(overlap):
        phase = i / (overlap - 1) * math.pi / 2
        loop[i] = samples[len(samples) - overlap + i] * math.cos(phase) + samples[i] * math.sin(phase)
    mean = sum(loop) / len(loop)
    centered = [sample - mean for sample in loop]
    rms = math.sqrt(sum(sample * sample for sample in centered) / len(centered))
    peak = max(abs(sample) for sample in centered)
    if not math.isfinite(rms) or rms == 0:
        raise ValueError(f"Source is silent or invalid: {source.name}")
    # Waves can contain brief splashes much louder than the bed. Smooth those peaks before matching levels.
    if peak / rms > 8:
        centered = [0.35 * math.tanh(sample * (0.07 / rms) / 0.35) for sample in centered]
        rms = math.sqrt(sum(sample * sample for sample in centered) / len(centered))
        peak = max(abs(sample) for sample in centered)
    gain = min(0.07 / rms, 0.58 / peak)
    pcm = array.array("h", (round(sample * gain * 32767) for sample in centered))
    out_rms = math.sqrt(sum(sample * sample for sample in pcm) / len(pcm)) / 32768
    out_peak = max(abs(sample) for sample in pcm) / 32768
    seam = abs(pcm[-1] - pcm[0]) / 32768
    max_step = max(abs(pcm[i] - pcm[i - 1]) for i in range(1, len(pcm))) / 32768
    assert out_peak < 0.6 and 0.035 < out_rms < 0.071, f"Unexpected output level: {source.name} rms={out_rms}, peak={out_peak}"
    assert seam <= max_step, "Loop seam exceeds normal sample changes"
    if sys.byteorder != "little":
        pcm.byteswap()
    target.write_bytes(pcm.tobytes())
    return {"seconds": len(pcm) / SAMPLE_RATE, "rms": out_rms, "peak": out_peak,
            "seam_step": seam, "max_step": max_step, "bytes": target.stat().st_size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, help="Reuse hash-checked downloaded MP3 files")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    target = root / "app/src/main/assets/focus"
    target.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="stillpoint-focus-audio-") as temporary:
        cache = args.cache or Path(temporary)
        cache.mkdir(parents=True, exist_ok=True)
        levels = {}
        for name, (url, expected_hash) in SOURCES.items():
            source = cache / f"{name}.mp3"
            if not source.exists():
                request = urllib.request.Request(url, headers={"User-Agent": "Stillpoint audio importer"})
                with urllib.request.urlopen(request, timeout=60) as response:
                    source.write_bytes(response.read())
            if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
                raise ValueError(f"Source checksum changed: {name}")
            levels[name] = import_loop(source, target / f"{name}.pcm")
        print(json.dumps(levels, indent=2))


if __name__ == "__main__":
    main()
