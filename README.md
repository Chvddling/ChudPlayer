# ChudPlayer

A NewPipe-style YouTube & SoundCloud player for Windows. No ads, no account, no tracking,
background audio, local playlists. It has a dark Windows 7 "smoked glass" look with an iTunes-style layout.

ChudPlayer uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), the same library
the Android [NewPipe](https://newpipe.net) app uses to read YouTube and SoundCloud, and
[mpv](https://mpv.io) for playback. It is not affiliated with TeamNewPipe.

## Download

1. Go to **[Releases](../../releases/latest)** and download `ChudPlayer-windows-x64.zip`.
2. Unzip it somewhere you can write to (for example `Documents\ChudPlayer` or `D:\Apps\ChudPlayer`).
   Avoid `Program Files`, because the auto-updater can't write there.
3. Run `ChudPlayer.exe`. Java is bundled, so you don't need to install anything.

## Staying up to date (automatically)

YouTube changes things often, and that's what breaks clients like NewPipe. ChudPlayer handles it on its own:

- **Daily check for NewPipe fixes.** Every day a GitHub Action checks for a new NewPipeExtractor
  release. When there is one, it updates the project, builds a new ChudPlayer and publishes it here.
- **Self-update.** At startup ChudPlayer checks this repo's releases. When a newer version exists, an orange
  **Update** button appears in the bottom bar. One click downloads it, replaces the files and restarts.
  Your playlists, history and settings are kept.
- **yt-dlp backup.** ChudPlayer keeps its own copy of [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
  updates it daily. yt-dlp is usually fixed within hours of a YouTube change. When NewPipeExtractor fails,
  ChudPlayer automatically searches and plays through yt-dlp instead, and shows an orange notice when it does.

Right-click the version text in the bottom-right corner to check for updates now, turn automatic checks
off and on, or update yt-dlp.

## Features

- Search YouTube or SoundCloud, or paste a link from either one
- Embedded video player (mpv) with quality picker, fullscreen (F11 / double-click), volume and seeking
- **Audio only** mode, which is NewPipe's background player
- **Mini player**: a small always-on-top window, while the main window gets minimized
- Queue, autoplay of related videos, watch history
- **Local playlists**: create, rename, reorder, play, shuffle or play in background
- **Right-click** any video or song for Play next, Add to queue, Add to playlist and
  Save current queue as playlist

Your data lives in `%APPDATA%\ChudPlayer\` (`playlists.json`, `history.json`, `settings.json`).

## Keyboard

| Key | Action |
|---|---|
| Space | Play / pause |
| ← / → | Seek 5 s |
| F11 | Fullscreen |
| Esc | Exit fullscreen |
| Media keys | Play/pause, next, previous (while the window has focus) |

When you click on the video, mpv's own shortcuts work too (`f`, `m`, `9`/`0` volume, `[`/`]` speed…).

## Building it yourself

1. Install JDK 21 (for example [Eclipse Temurin](https://adoptium.net)).
2. Download the newest `mpv-dev-x86_64-*.7z` from
   [shinchiro/mpv-winbuild-cmake](https://github.com/shinchiro/mpv-winbuild-cmake/releases) and copy
   `libmpv-2.dll` into `native-resources\windows\`.
3. Double-click `build.bat` and choose **1** to run the app or **2** for the portable folder. It writes everything to `build-log.txt`.

The NewPipeExtractor version is in `extractor.version`.

## Project layout

```
src/main/kotlin/newpipe/desktop/
  Main.kt                    window, layout, keyboard shortcuts
  net/Http.kt                network layer that NewPipeExtractor uses
  data/YouTubeRepo.kt        YouTube + SoundCloud search, stream info, stream selection
  data/Storage.kt            settings, history, playlists
  player/Mpv.kt              libmpv binding (JNA)
  player/PlayerController.kt queue, playback, audio mode, yt-dlp backup
  update/Updater.kt          self-update from GitHub releases
  update/YtDlp.kt            self-updating yt-dlp backup
  ui/                        the Smoked Glass theme and all screens
.github/workflows/build.yml  daily NewPipeExtractor check + Windows build + release
```

## License

GPL-3.0, like NewPipe and NewPipeExtractor.
