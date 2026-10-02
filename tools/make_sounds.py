#!/usr/bin/env python3
"""Synthesize Stillpoint's sound effects into app/src/main/res/raw/sfx_*.ogg.

Every sound is generated here from sine partials and filtered noise, so the app ships
no third-party audio. Run it again after a change: python3 tools/make_sounds.py
It needs numpy and ffmpeg with libvorbis.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def note(name: str) -> float:
    """Frequency of a note name such as 'C5' or 'F#4'."""
    steps = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((steps[pitch] + (octave - 4) * 12) / 12)


def silence(seconds: float) -> np.ndarray:
    return np.zeros(int(RATE * seconds))


def envelope(n: int, attack: float, decay: float) -> np.ndarray:
    """A quick linear attack, then an exponential decay with time constant [decay] seconds."""
    t = np.arange(n) / RATE
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay)


def marimba(freq: float, seconds: float = 0.35, decay: float = 0.09) -> np.ndarray:
    """A soft wooden mallet: the fundamental plus a fast-fading fourth harmonic."""
    t = np.arange(int(RATE * seconds)) / RATE
    body = np.sin(2 * np.pi * freq * t) * envelope(len(t), 0.003, decay)
    knock = 0.35 * np.sin(2 * np.pi * freq * 4 * t) * envelope(len(t), 0.001, decay / 5)
    return body + knock


def bell(freq: float, seconds: float = 0.9, decay: float = 0.35) -> np.ndarray:
    """A bright bell with slightly inharmonic partials."""
    t = np.arange(int(RATE * seconds)) / RATE
    out = np.zeros_like(t)
    for ratio, gain, d in ((1, 1.0, 1.0), (2.0, 0.45, 0.6), (3.01, 0.25, 0.4), (4.2, 0.12, 0.25)):
        out += gain * np.sin(2 * np.pi * freq * ratio * t) * envelope(len(t), 0.002, decay * d)
    return out


def glide(start: float, end: float, seconds: float, decay: float) -> np.ndarray:
    """A sine that slides from [start] to [end] Hz, for pops and bonks."""
    n = int(RATE * seconds)
    freq = np.geomspace(start, end, n)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    return np.sin(phase) * envelope(n, 0.002, decay)


def whoosh(seconds: float, rising: bool = True) -> np.ndarray:
    """Noise through a moving low-pass filter, like air rushing past."""
    n = int(RATE * seconds)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n)
    cutoff = np.linspace(0.02, 0.25, n) if rising else np.linspace(0.25, 0.02, n)
    out = np.zeros(n)
    y = 0.0
    for i in range(n):
        y += cutoff[i] * (noise[i] - y)
        out[i] = y
    shape = np.sin(np.linspace(0, np.pi, n)) ** 2
    return out * shape


def mix(*parts: tuple[float, np.ndarray]) -> np.ndarray:
    """Places each sound at its start time in seconds and sums them."""
    end = max(int(RATE * at) + len(s) for at, s in parts)
    out = np.zeros(end)
    for at, s in parts:
        i = int(RATE * at)
        out[i:i + len(s)] += s
    return out


def save(name: str, samples: np.ndarray, peak_db: float):
    fade = min(len(samples), int(RATE * 0.01))
    samples = samples.copy()
    samples[-fade:] *= np.linspace(1, 0, fade)
    samples *= 10 ** (peak_db / 20) / max(np.abs(samples).max(), 1e-9)
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((samples * 32767).astype(np.int16).tobytes())
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(OUT / f"sfx_{name}.ogg")],
            check=True,
        )


def main():
    # Taps and toggles are quiet. Rewards are louder and longer.
    save("tap", glide(950, 420, 0.06, 0.018), -16)
    save("toggle_on", mix((0, marimba(note("C6"), 0.18)), (0.06, marimba(note("G6"), 0.25))), -10)
    save("toggle_off", mix((0, marimba(note("G5"), 0.18)), (0.06, marimba(note("C5"), 0.25))), -12)
    save("select", mix((0, marimba(note("A5"), 0.3)), (0, 0.3 * marimba(note("E6"), 0.3))), -9)
    save("start", mix(
        (0, 0.35 * whoosh(0.45)),
        (0.05, marimba(note("C5"))), (0.12, marimba(note("E5"))), (0.19, marimba(note("G5"))),
        (0.26, bell(note("C6"), 0.7)),
    ), -6)
    save("complete", mix(
        (0, marimba(note("C5"))), (0.09, marimba(note("E5"))), (0.18, marimba(note("G5"))),
        (0.3, bell(note("C6"), 1.1, 0.45)), (0.3, 0.7 * bell(note("E6"), 1.1, 0.45)), (0.3, 0.5 * bell(note("G6"), 1.1, 0.45)),
    ), -4)
    sparkle = mix(*[(0.05 * i, 0.35 * bell(note(n), 0.5, 0.15)) for i, n in enumerate(["E7", "G7", "C7", "A7", "E7", "C8"])])
    save("level_up", mix(
        *[(0.07 * i, marimba(note(n))) for i, n in enumerate(["C5", "E5", "G5", "C6", "E6", "G6"])],
        (0.45, bell(note("C7"), 1.2, 0.5)), (0.45, 0.6 * bell(note("G6"), 1.2, 0.5)),
        (0.55, sparkle),
    ), -3)
    save("streak", mix(
        (0, 0.6 * whoosh(0.5)),
        (0.25, bell(note("F5"), 0.9, 0.4)), (0.25, 0.7 * bell(note("A5"), 0.9, 0.4)), (0.25, 0.6 * bell(note("C6"), 0.9, 0.4)),
    ), -5)
    save("quest", mix((0, bell(note("E6"), 0.6, 0.22)), (0.1, bell(note("B6"), 0.7, 0.28))), -7)
    save("give_up", mix(
        (0, 0.9 * glide(note("G4"), note("F#4"), 0.28, 0.12)),
        (0.22, glide(note("D4"), note("C#4"), 0.45, 0.2)),
    ), -9)
    save("block", mix((0, glide(260, 150, 0.22, 0.07)), (0, 0.25 * glide(1200, 500, 0.04, 0.01))), -9)
    print("Wrote", ", ".join(sorted(p.name for p in OUT.glob("sfx_*.ogg"))))


if __name__ == "__main__":
    main()
