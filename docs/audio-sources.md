# UI audio sources

UI and onboarding sounds come from [Interface Sounds 1.0 by Kenney](https://kenney.nl/assets/interface-sounds).
The library uses [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).
It permits commercial use, modification, and redistribution. The original notice is in `licenses/Kenney-Interface-Sounds-CC0.txt`.

Source archive SHA-256: `f2193d072726d6758a5f7871b2dcc54dcce0d5c35c6f0a62f92549b327c81232`.

| App sound | Original file | Peak target |
| --- | --- | --- |
| Tap | click_001.ogg | -23 dBFS |
| Toggle on | drop_002.ogg | -20 dBFS |
| Toggle off | drop_003.ogg | -21 dBFS |
| Select | bong_001.ogg | -22 dBFS |
| Start focus | maximize_006.ogg | -17 dBFS |
| Complete focus | confirmation_001.ogg | -16 dBFS |
| Level up | confirmation_004.ogg | -17 dBFS |
| Streak | select_005.ogg | -18 dBFS |
| Quest | maximize_008.ogg | -18 dBFS |
| Give up | minimize_006.ogg | -20 dBFS |
| Block | back_004.ogg | -19 dBFS |
| Welcome | drop_004.ogg | -19 dBFS |
| Question | question_004.ogg | -22 dBFS |
| Slide | switch_003.ogg | -25 dBFS |
| Notification box | switch_007.ogg | -24 dBFS |

`python3 tools/import_sounds.py` downloads the checked archive and prepares the selected files with ffmpeg.
Processing preserves the original timing and pitch. It converts to mono, removes frequencies below 35 Hz,
softens frequencies above 2.4 kHz, and lowers each peak. Short fades remove abrupt starts and ends.
The Ogg encoder can change peak levels slightly. These targets leave ample headroom.

Onboarding cues play once per slide. Plan sounds follow the check marks. Notification sounds follow the falling messages.
Leaving a slide cancels its remaining cues. Looping artwork does not produce an endless sound loop.
All cues follow the Sound effects setting and media volume.

Focus ambience uses bundled CC0 recordings. See [focus audio sources](focus-audio-sources.md).
Routine navigation, badge taps, and ordinary controls are silent.
