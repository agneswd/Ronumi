#!/usr/bin/env python3
"""Build the app's UI sounds from recorded instruments. Needs numpy and ffmpeg.

Each cue is a short phrase played on real marimba, vibraphone, glockenspiel or bell tree
recordings from the Versilian Community Sample Library (CC0). See docs/audio-sources.md.
Run it again after a change: python3 tools/import_sounds.py
"""
import pathlib
import subprocess
import urllib.parse
import urllib.request

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/raw"
CACHE = pathlib.Path.home() / ".cache/stillpoint-vcsl"
# A fixed commit, so the same script always builds the same sounds.
COMMIT = "c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e"
BASE = f"https://raw.githubusercontent.com/sgossner/VCSL/{COMMIT}/"
RATE = 44100

# Recorded notes for each instrument. Other notes are pitched from the nearest recording.
MARIMBA = "Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_{}_med_01.wav"
VIBES = "Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_{}_v1_rr1_Main.wav"
GLOCK = "Idiophones/Struck Idiophones/Glockenspiel/glock_soft_{}.wav"
SAMPLES = {
    "marimba": {n: MARIMBA.format(n) for n in ("C4", "G4", "B4", "F5", "C6")},
    "vibes": {n: VIBES.format(n) for n in ("G3", "B3", "D4", "F4", "A4", "C5", "E5")},
    "glock": {"G5": GLOCK.format("G5_01"), "C6": GLOCK.format("C6_01"), "G6": GLOCK.format("G6_01"), "C7": GLOCK.format("C7_03")},
}
BELL_TREE = "Idiophones/Struck Idiophones/Bell Tree/Stroke/BellTree_Stroke_1_Mid.wav"
NAMES = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def midi(name: str) -> int:
    return NAMES[name[:-1]] + 12 * (int(name[-1]) + 1)


def load(path: str) -> np.ndarray:
    """Downloads a recording once, then reads it as mono float samples from its first hit."""
    file = CACHE / path
    if not file.exists():
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(urllib.request.urlopen(BASE + urllib.parse.quote(path), timeout=60).read())
    pcm = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(file), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"])
    samples = np.frombuffer(pcm, dtype=np.float32).astype(np.float64)
    start = int(np.argmax(np.abs(samples) > 0.05 * np.abs(samples).max()))
    return samples[max(0, start - int(RATE * 0.002)):]


def note(instrument: str, name: str, gain: float = 1.0, seconds: float = 0.8) -> np.ndarray:
    """One note, pitched from the nearest recording. Small shifts keep the instrument's character."""
    target = midi(name)
    source = min(SAMPLES[instrument], key=lambda n: abs(midi(n) - target))
    samples = load(SAMPLES[instrument][source])
    ratio = 2 ** ((target - midi(source)) / 12)
    pitched = np.interp(np.arange(0, len(samples) - 1, ratio), np.arange(len(samples)), samples)
    return gain * fade(pitched[: int(RATE * seconds)], 0.08) / np.abs(samples).max()


def fade(samples: np.ndarray, seconds: float) -> np.ndarray:
    n = min(len(samples), int(RATE * seconds))
    out = samples.copy()
    out[len(out) - n:] *= np.linspace(1, 0, n) ** 2
    return out


def mix(*parts: tuple[float, np.ndarray]) -> np.ndarray:
    """Places each sound at its start time in seconds and sums them."""
    out = np.zeros(max(int(RATE * at) + len(s) for at, s in parts))
    for at, s in parts:
        out[int(RATE * at): int(RATE * at) + len(s)] += s
    return out


def run(instrument: str, names: str, step: float, gain: float = 1.0, seconds: float = 0.8):
    """Notes one after another, [step] seconds apart."""
    return [(i * step, note(instrument, n, gain, seconds)) for i, n in enumerate(names.split())]


def save(name: str, samples: np.ndarray, peak_db: float):
    samples = fade(samples, 0.03) * 10 ** (peak_db / 20) / np.abs(samples).max()
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f64le", "-ar", str(RATE), "-ac", "1", "-i", "-",
        "-af", "highpass=f=60", "-c:a", "libvorbis", "-q:a", "5", str(OUT / f"sfx_{name}.ogg")],
        input=samples.tobytes(), check=True)
    print(f"sfx_{name}.ogg {len(samples) / RATE:.2f}s {peak_db} dBFS")


def main():
    # Quiet, short cues for controls. Longer phrases for rewards. Everything is in C major.
    save("tap", note("marimba", "G5", seconds=0.18), -24)
    save("toggle_on", mix(*run("marimba", "C5 G5", 0.07, seconds=0.3)), -21)
    save("toggle_off", mix(*run("marimba", "G5 C5", 0.07, seconds=0.3)), -22)
    save("select", note("vibes", "E5", seconds=0.4), -22)
    save("slide", note("vibes", "C5", seconds=0.45), -26)
    save("question", mix(*run("vibes", "C5 D5", 0.12, seconds=0.5)), -23)
    save("notification", mix((0, note("glock", "C7", 0.7, 0.5)), (0.06, note("glock", "G6", 0.5, 0.5))), -25)
    save("welcome", mix(*run("vibes", "G4 C5 E5", 0.09, seconds=0.6), (0.27, note("glock", "C6", 0.5, 0.7))), -19)
    save("start", mix(*run("marimba", "C4 E4 G4 C5", 0.07, seconds=0.6), (0.28, note("glock", "C6", 0.6, 0.9))), -17)
    save("quest", mix(*run("glock", "G5 C6", 0.1, 0.9, 0.8)), -19)
    save("complete", mix(
        *run("marimba", "C5 E5 G5", 0.08, seconds=0.4),
        *[(0.26, note("vibes", n, 0.8, 1.2)) for n in ("C5", "E5", "G5")],
        (0.26, note("glock", "C7", 0.35, 1.0)),
    ), -16)
    save("level_up", mix(
        *run("marimba", "C4 D4 E4 G4 A4 C5 E5 G5", 0.055, seconds=0.5),
        (0.44, note("glock", "C6", 0.8, 1.2)), (0.44, note("glock", "G6", 0.6, 1.2)),
        (0.46, 0.45 * fade(load(BELL_TREE)[: int(RATE * 1.4)], 0.5) / np.abs(load(BELL_TREE)).max()),
    ), -16)
    save("streak", mix(
        (0, 0.5 * fade(load(BELL_TREE)[: int(RATE * 1.0)], 0.4) / np.abs(load(BELL_TREE)).max()),
        *[(0.18, note("vibes", n, 0.8, 1.1)) for n in ("F4", "A4", "C5")],
    ), -18)
    save("give_up", mix(*run("vibes", "E4 C4", 0.22, seconds=0.9)), -21)
    save("block", mix(*run("marimba", "G4 D4", 0.09, seconds=0.35)), -20)


if __name__ == "__main__":
    main()
