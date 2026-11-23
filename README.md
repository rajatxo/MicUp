# MicUp

> Voice rooms that never duck your game sound. Built for gamers who want crisp squad comms alongside full-volume gameplay audio.

MicUp is a universal voice chat app for Android that solves one specific problem: when you use Discord or any in-game voice chat, Android enters `MODE_IN_COMMUNICATION` and auto-ducks every other audio stream — gun sounds, footsteps, music — down to ~20% volume. **MicUp never enters communication mode.** The OS treats your voice chat as a game sound effect, so both your squad's voice AND the game's audio play at full volume simultaneously.

Works alongside any game — Free Fire, BGMI, CODM, Mobile Legends, anything.

## How it works

| Setting | Discord / FF in-game voice (causes ducking) | MicUp (no ducking) |
|---|---|---|
| Audio mode | `MODE_IN_COMMUNICATION` | `MODE_NORMAL` |
| AudioTrack usage | `USAGE_VOICE_COMMUNICATION` | `USAGE_GAME` |
| AEC/NS | Hardware (only in comm mode) | Software (WebRTC AEC3 + NS) |

The OS sees MicUp's voice chat as a **game sound effect**, not a phone call → no ducking → squad voice + game audio both at full volume.

**Powered by WebRTC** — a free peer-to-peer mesh, no paid voice service. Rooms are capped at **5 people** (you + 4 friends), which is the practical limit for a mesh and plenty for a squad. Voice is routed to the loudspeaker on phones that would otherwise put it in the earpiece.

## Get the APK

1. Go to **https://github.com/rajatxo/MicUp/releases/latest**
2. Download `MicUp-vX.X.X.apk`
3. Install on your phone (enable "Install unknown apps" for your browser)
4. Open MicUp → grant mic permission → **Create Room** → share the 5-character code with your squad

## How to use during gameplay

1. **Open MicUp first** → Create/Join room → confirm you can hear your squad
2. **Then open Free Fire** (or any game)
3. **Turn OFF the in-game mic** in Free Fire settings
4. MicUp keeps running in the background (notification shows "Voice Room")
5. Play normally — squad voice + game audio both at full volume

## Background behavior and ending a room

The call lives in a foreground service, not in the screen you see:

- **Closing the app or swiping it away from the task manager does NOT end the room.** You can reopen MicUp later and you'll still be in the same room.
- The notification shows **"MicUp — Voice Room"** with an **End chat** button. Tap it to leave without opening the app.
- **Leaving only drops you.** Your friends stay in the room — there is no host who can kick everyone. The room ends only when its last participant leaves.
- If Android asks, allow notifications for MicUp so you can see the room status and the End chat button.

## Speaker, headphones, and Bluetooth

Tap **🔊 Test speaker** on the join screen to hear a short beep through the same route the voice call uses. Tap **🎤 Test mic** to record 2 seconds and play it back — this confirms the mic works (and shows Android's mic-in-use dot) without a second phone.

- **Phone speaker:** voice goes to the loudspeaker, not the small earpiece.
- **Wired headset:** voice and game audio go to the headset; the headset mic is used.
- **Bluetooth / AirPods / neckband:** voice and game audio go to the headset over A2DP. The mic stays on the phone so game audio is never ducked. (Android cannot use a Bluetooth headset mic without switching to a call route, which ducks the game.)
- Bluetooth or wired audio connected before or during a call takes priority over the speaker; MicUp re-routes automatically when you plug or unplug a headset.

**About the green mic dot:** MicUp only opens the mic when it is actually sending your voice to someone. Alone in a room there is nobody to send to, so the mic stays closed and no dot appears — that is normal, not a broken mic. The dot shows up once a friend joins and the connection starts. Use **🎤 Test mic** to check the mic on your own.

---

Made with love in India 🇮🇳

Developer: https://t.me/rajat_xo · Support: https://t.me/coralmusicsupport
