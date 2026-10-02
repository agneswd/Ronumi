# UI audio sources

UI and onboarding sounds are short phrases played on recorded instruments from the
[Versilian Community Sample Library](https://github.com/sgossner/VCSL) (VCSL).
The library uses [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).
It permits commercial use, modification, and redistribution. The license text is in `licenses/VCSL-CC0.txt`.

`python3 tools/import_sounds.py` builds every cue. It downloads the recordings from a fixed VCSL commit,
pitches notes from the nearest recording, places them in time, and writes Ogg files with ffmpeg.
All cues are in C major. Processing keeps the full frequency range, removes rumble below 60 Hz, and lowers each peak.

| App sound | Instruments and notes | Peak target |
| --- | --- | --- |
| Tap | Marimba G5 | -24 dBFS |
| Toggle on | Marimba C5, G5 | -21 dBFS |
| Toggle off | Marimba G5, C5 | -22 dBFS |
| Select | Vibraphone E5 | -22 dBFS |
| Slide | Vibraphone C5 | -26 dBFS |
| Question | Vibraphone C5, D5 | -23 dBFS |
| Notification box | Glockenspiel C7, G6 | -25 dBFS |
| Welcome | Vibraphone G4, C5, E5, glockenspiel C6 | -19 dBFS |
| Start focus | Marimba C4, E4, G4, C5, glockenspiel C6 | -17 dBFS |
| Quest | Glockenspiel G5, C6 | -19 dBFS |
| Complete focus | Marimba C5, E5, G5, vibraphone C major chord, glockenspiel C7 | -16 dBFS |
| Level up | Marimba run from C4 to G5, glockenspiel C6 and G6, bell tree | -16 dBFS |
| Streak | Bell tree, vibraphone F major chord | -18 dBFS |
| Give up | Vibraphone E4, C4 | -21 dBFS |
| Block | Marimba G4, D4 | -20 dBFS |

Onboarding cues play once per slide. Plan sounds follow the check marks. Notification sounds follow the falling messages.
Leaving a slide cancels its remaining cues. Looping artwork does not produce an endless sound loop.
All cues follow the Sound effects setting and media volume.

Focus ambience uses bundled CC0 recordings. See [focus audio sources](focus-audio-sources.md).
Routine navigation, badge taps, and ordinary controls are silent.
