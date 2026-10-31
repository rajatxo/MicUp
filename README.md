# MicUp

> Voice rooms that never duck your game sound. Built for gamers who want crisp squad comms alongside full-volume gameplay audio.

MicUp is a universal voice chat app for Android that solves one specific problem: when you use Discord or any in-game voice chat, Android enters `MODE_IN_COMMUNICATION` and auto-ducks every other audio stream — gun sounds, footsteps, music — down to ~20% volume. **MicUp never enters communication mode.** The OS treats your voice chat as a game sound effect, so both your squad's voice AND the game's audio play at full volume simultaneously.

Works alongside any game — Free Fire, BGMI, CODM, Mobile Legends, anything.

## How it works

| Setting | Discord / FF in-game voice (causes ducking) | MicUp (no ducking) |
|---|---|---|
| Audio mode | `MODE_IN_COMMUNICATION` | `MODE_NORMAL` |
| AudioTrack usage | `USAGE_VOICE_COMMUNICATION` | `USAGE_GAME` |
| AEC/NS | Hardware (only in comm mode) | Software (WebRTC AEC3 + NS + AGC) |

The OS sees MicUp's voice chat as a **game sound effect**, not a phone call → no ducking → squad voice + game audio both at full volume.

## Get the APK

1. Go to **https://github.com/rajatxo/MicUp/actions**
2. Click the latest green build
3. Download `micup-universal-release-apk`
4. Install on your phone (enable "Install unknown apps" for your browser)
5. Open MicUp → grant mic permission → **Create Room** → share the 6-character code with your squad

## How to use during gameplay

1. **Open MicUp first** → Create/Join room → confirm you can hear your squad
2. **Then open Free Fire** (or any game)
3. **Turn OFF the in-game mic** in Free Fire settings
4. MicUp keeps running in the background (notification shows "Voice Room")
5. Play normally — squad voice + game audio both at full volume

---

Made with love in India 🇮🇳

Developer: https://t.me/rajat_xo · Support: https://t.me/coralmusicsupport
