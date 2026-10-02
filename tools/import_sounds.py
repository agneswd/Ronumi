#!/usr/bin/env python3
"""Prepare quiet CC0 library sounds. Needs ffmpeg; see docs/audio-sources.md."""
import array
import hashlib
import pathlib
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = "https://kenney.nl/media/pages/assets/interface-sounds/fa43c1dd4d-1677589452/kenney_interface-sounds.zip"
SHA256 = "f2193d072726d6758a5f7871b2dcc54dcce0d5c35c6f0a62f92549b327c81232"
# Output name: original filename, peak level in dBFS.
SOUNDS = {
    "tap": ("click_001", -23),
    "toggle_on": ("drop_002", -20),
    "toggle_off": ("drop_003", -21),
    "select": ("bong_001", -22),
    "start": ("maximize_006", -17),
    "complete": ("confirmation_001", -16),
    "level_up": ("confirmation_004", -17),
    "streak": ("select_005", -18),
    "quest": ("maximize_008", -18),
    "give_up": ("minimize_006", -20),
    "block": ("back_004", -19),
    "welcome": ("drop_004", -19),
    "question": ("question_004", -22),
    "slide": ("switch_003", -25),
    "notification": ("switch_007", -24),
}


def main():
    data = urllib.request.urlopen(URL, timeout=30).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise ValueError("The library archive changed. Review it before replacing sounds.")
    with tempfile.TemporaryDirectory() as directory:
        archive = pathlib.Path(directory) / "library.zip"
        archive.write_bytes(data)
        with zipfile.ZipFile(archive) as library:
            for name, (source, db) in SOUNDS.items():
                original = pathlib.Path(directory) / "source.ogg"
                original.write_bytes(library.read("Audio/" + source + ".ogg"))
                pcm = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(original),
                    "-af", "highpass=f=35,lowpass=f=2400", "-ac", "1", "-ar", "44100", "-f", "f32le", "-"])
                samples = array.array("f", pcm)
                gain = 10 ** (db / 20) / max(abs(value) for value in samples)
                seconds = len(samples) / 44100
                output = ROOT / "app/src/main/res/raw" / ("sfx_" + name + ".ogg")
                subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", "44100", "-ac", "1", "-i", "-",
                    "-af", f"volume={gain},afade=t=in:d=0.007,afade=t=out:st={max(0, seconds - .02)}:d=0.02",
                    "-c:a", "libvorbis", "-q:a", "5", str(output)], input=pcm, check=True)
                print(name, source, f"{seconds:.2f}s", f"{db} dBFS")


if __name__ == "__main__":
    main()
