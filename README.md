# ChudPlayer

An all-in-one player for Windows: YouTube, SoundCloud, Squidify and your own music files. No ads, no account, no tracking,
background audio, local playlists. It has a dark Windows 7 "smoked glass" look with an iTunes-style layout.

ChudPlayer uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), the same library
the Android [NewPipe](https://newpipe.net) app uses to read YouTube and SoundCloud, and
[mpv](https://mpv.io) for playback. It is not affiliated with TeamNewPipe.

<img width="500" height="400" alt="Screenshot 2026-11-25 031947" src="https://github.com/user-attachments/assets/979657df-360a-4974-b312-24cb76e2dbcb" />


## Phone remote (Android)

**ChudPlayer Remote** controls ChudPlayer from your phone over your home Wi-Fi. You can see what's playing,
play/pause/skip, seek, change the volume (the phone's volume buttons work too), switch repeat/shuffle/loop/autoplay,
search YouTube/SoundCloud/Squidify, manage the queue (drag to reorder, swipe to remove), and play your playlists,
history and My Music folders. Your phone doesn't play the music; ChudPlayer on the PC does.

1. In ChudPlayer open **Settings → Phone remote** and turn it on. It's off by default, and when it's off it doesn't run at all.
2. Download `ChudPlayerRemote.apk` from **[Releases](../../releases/latest)** on your phone and install it
   (Android asks you to allow installing from your browser the first time).
3. Open the app, tap **Scan QR code** and scan the code shown in ChudPlayer's Settings.
   If Windows asks about the firewall, allow ChudPlayer on **private** networks.

The phone only connects inside your home network and needs the pairing code from the QR code.
To lock out old phones, use "New pairing code" in Settings.

The APK is signed with a key kept in this repo (`android/keystore`), so every new APK installs over the old one.
That also means anyone could sign an APK with the same key, so only install ChudPlayer Remote from this repo's Releases.

<p float="left">
  <img width="280" height="585" alt="Screenshot_20261125_032403_ChudPlayer Remote" src="https://github.com/user-attachments/assets/26f5b34d-fbec-4445-b845-8bd95af7ac7f" />
  <img width="280" height="585" alt="22" src="https://github.com/user-attachments/assets/ed9c61d3-87ac-462a-8f2a-58ac9186062a" / 

</p>

>


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
- **Squidify** (www.squidify.net): game, anime, movie & TV soundtracks, often lossless, through its public
  Subsonic/Navidrome API (guest login). Search songs, artists and albums; open artist and album pages.
- **My Music**: browse the music folders on your PC in a folder tree and play your files
  (mp3, flac, wav, ogg, opus, m4a/aac, wma, aiff, ape, …) with their tags and cover art.
  Choose the main music folder in **Settings**; the last opened folder is remembered.
- Playback modes in the top bar: repeat song, shuffle, loop queue, autoplay
- Hold and drag songs in the queue or your playlists to reorder them
- **System tray**: send ChudPlayer to the tray ("To tray" button, or make the close button do it in Settings);
  click the tray icon to bring it back, right-click it for play/pause, next, previous and exit
- Channel / artist pages (uploads newest first, playlists, search inside a channel), history search
- Embedded video player (mpv) with quality picker, fullscreen (F11 / double-click), volume and seeking
- **Mini player**: a small always-on-top window, while the main window gets minimized
- Queue, autoplay of related videos, watch history
- **Local playlists**: create, rename, reorder, play, shuffle or play in background


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

## Previews
<img width="961" height="767" alt="Screenshot 2026-11-25 032348" src="https://github.com/user-attachments/assets/fd9f0c4a-1128-46ad-99f3-e98e15565621" />
<img width="964" height="766" alt="Screenshot 2026-11-25 032333" src="https://github.com/user-attachments/assets/7da418e3-bce3-4de8-9198-59c7fd8c0f4c" />
<img width="966" height="769" alt="Screenshot 2026-11-25 032120" src="https://github.com/user-attachments/assets/fdffce82-d05a-46c9-97b1-69519efb572e" />
<img width="960" height="776" alt="Screenshot 2026-11-25 032050" src="https://github.com/user-attachments/assets/177d5760-4672-4dd0-969c-50dfe4eaeb9b" />
<img width="959" height="768" alt="Screenshot 2026-11-25 031947" src="https://github.com/user-attachments/assets/5cef4106-2df7-48f8-94c9-2cecf6e7bd8a" />
<img width="501" height="158" alt="Screenshot 2026-11-25 031841" src="https://github.com/user-attachments/assets/eac4d0d6-0cba-4f99-8888-8c1dd43c5cae" />


## License

GPL-3.0, like NewPipe and NewPipeExtractor.
