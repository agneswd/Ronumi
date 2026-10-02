# Focus audio sources

Stillpoint plays bundled recordings from Freesound. It does not synthesize focus audio in the app.
Playback works offline. The app does not download audio or need Internet access.

All five source pages list **CC0 1.0 Universal**, checked on 2026-10-02.
CC0 permits commercial reuse, modification, and distribution without required attribution.
The credits below retain each creator's name and source page.
See the [CC0 summary](https://creativecommons.org/publicdomain/zero/1.0/) and [full legal text](https://creativecommons.org/publicdomain/zero/1.0/legalcode).

White, pink, and brown noise are library-produced noise recordings. Rain and waves are field recordings.
The packaged files use Freesound's public high-quality MP3 previews as their source.

| App sound | Recording | Creator | License |
| --- | --- | --- | --- |
| White | [White noise](https://freesound.org/people/theundecided/sounds/165058/) | theundecided | CC0-1.0 |
| Pink | [TONE pink noise 44.1 16bit.wav](https://freesound.org/people/klangfabrik/sounds/28639/) | klangfabrik | CC0-1.0 |
| Brown | [Brown Noise (Medium) - (OldSlowVideogamer).mp3](https://freesound.org/people/OldSlowVideogamer/sounds/365932/) | OldSlowVideogamer | CC0-1.0 |
| Rain | [Gentle Rain.wav](https://freesound.org/people/shelbyshark/sounds/501243/) | shelbyshark | CC0-1.0 |
| Waves | [Slow Waves, Stony Beach](https://freesound.org/people/Kinoton/sounds/635917/) | Kinoton | CC0-1.0 |

## Rebuild the audio

Install Python 3 and ffmpeg. Run `python3 tools/import_focus_sounds.py` from the repository.
Use `--cache /path/to/downloads` to reuse downloaded MP3 files.
The script checks each source SHA-256 before conversion. A changed download stops the import.

The importer skips the first two seconds and takes up to 46 seconds of each recording.
It removes low-frequency rumble and softens frequencies above 5 kHz.
A two-second equal-power crossfade joins each loop's end and start.
Brief loud splashes receive smooth peak compression before volume matching.
No new audio is synthesized. No playback speed or pitch changes are applied.

Output files are mono, 22,050 Hz, signed 16-bit little-endian PCM.
Each loop lasts 44 seconds, except pink noise, which lasts 26 seconds.
The player adds a 250 ms start fade and a 180 ms stop fade.
Sound changes fade the old recording out before the new recording starts.

## Source checksums

- White: [MP3 source](https://cdn.freesound.org/previews/165/165058_947433-hq.mp3)
  SHA-256: `5aca5a2faa33784956d489816cd2f82731e751da890c4283436c7e31ae988927`
- Pink: [MP3 source](https://cdn.freesound.org/previews/28/28639_181941-hq.mp3)
  SHA-256: `73513db7631a68a2804ca33b69f9ac220e8faeda10906a95f4f8e4a8196c7497`
- Brown: [MP3 source](https://cdn.freesound.org/previews/365/365932_5857547-hq.mp3)
  SHA-256: `fe0789085e5292533217431e3f413580421aeb3efe283c5a99fc14ec2909b790`
- Rain: [MP3 source](https://cdn.freesound.org/previews/501/501243_8644110-hq.mp3)
  SHA-256: `5fffe1bb4acdd51270b0fa84614be3fa354155b08f60881f4fe12fd0ff53f423`
- Waves: [MP3 source](https://cdn.freesound.org/previews/635/635917_2247456-hq.mp3)
  SHA-256: `e11d9a8d01ce9cfac82b2eb8bb75be4b4ce7a13b0bc03ec881115dc84118f553`

## Imported levels

RMS measures average signal level. Peak measures the largest sample. Both use 1.0 as full scale.
All files have an RMS level of 0.070. None reach full scale or clip.

| Sound | Peak | RMS | Loop seam step |
| --- | ---: | ---: | ---: |
| White | 0.2580 | 0.0700 | 0.1224 |
| Pink | 0.3653 | 0.0700 | 0.0141 |
| Brown | 0.3038 | 0.0700 | 0.0022 |
| Rain | 0.3750 | 0.0700 | 0.0018 |
| Waves | 0.4074 | 0.0700 | 0.0017 |

Each loop seam step is smaller than the largest normal sample step in that recording.
The importer checks duration, peak, RMS, and seam continuity on each run.
