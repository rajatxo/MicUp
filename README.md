# MicUp

> Voice rooms that never duck your game sound. Built for gamers who want crisp squad comms alongside full-volume gameplay audio.

MicUp is a universal voice chat app for Android that solves one specific problem: when you use Discord or any in-game voice chat, Android enters `MODE_IN_COMMUNICATION` and auto-ducks every other audio stream — gun sounds, footsteps, music — down to ~20% volume. **MicUp never enters communication mode.** The OS treats your voice chat as a game sound effect, so both your squad's voice AND the game's audio play at full volume simultaneously.

Works alongside any game — Free Fire, BGMI, CODM, Mobile Legends, anything.

## How it works (the no-ducking trick)

| Setting | Discord / FF in-game voice (causes ducking) | MicUp (no ducking) |
|---|---|---|
| Audio mode | `MODE_IN_COMMUNICATION` | `MODE_NORMAL` |
| AudioTrack usage | `USAGE_VOICE_COMMUNICATION` | `USAGE_GAME` |
| Audio focus | Transient focus that ducks others | No focus request |
| WebRTC comm mode | `setCommunicationMode(true)` | `setCommunicationMode(false)` |
| AEC/NS | Hardware (only works in comm mode) | Software (WebRTC AEC3 + NS + AGC) |
| Audio route | Earpiece / speaker-tilted | Speaker / headphones, normal media path |

The OS sees MicUp's voice chat as a **game sound effect**, not a phone call → no ducking triggered → your squad's voice AND the game's gun sounds / footsteps both come through at full volume.

Tech: WebRTC (Opus 48kHz, software AEC3/NS/AGC), full P2P mesh topology for 2-6 person squads.

---

## Get the APK

Every push to `main` triggers a GitHub Actions build that produces signed APKs:

1. Go to **https://github.com/rajatxo/MicUp/actions**
2. Click the latest green build
3. Scroll to **Artifacts** → download `micup-release-apk` (22 MB)
4. Install on your phone (enable "Install unknown apps" for your browser first)
5. Open MicUp → grant mic permission → **Create Room** → share the 6-character code with your squad

⚠️ **Important:** Until you set up the signaling server (below), the app will install and look perfect but won't actually connect you with friends. The default URL points at `wss://relay.micup.app/signal` which doesn't exist yet — you need to deploy the signaling server.

---

## Deploy the signaling server (free, 2 minutes)

The signaling server is what lets two MicUp clients find each other and exchange WebRTC connection info. Without it, the app can't establish a voice session. **It's free on Cloudflare Workers** and takes 2 minutes:

### Option A — Cloudflare dashboard (no CLI, easiest)

1. Go to **https://dash.cloudflare.com** → sign up (free) or log in
2. **Workers & Pages** → **Create application** → **Create Worker**
3. Name it `micup-signal` → click **Deploy**
4. Click **Edit code** → delete the template, paste the entire contents of [`server/worker.js`](./server/worker.js) → click **Deploy**
5. Copy your worker URL — looks like `wss://micup-signal.YOUR_SUBDOMAIN.workers.dev`
6. In MicUp app → Settings → Signaling Server → paste the URL

### Option B — Wrangler CLI (if you have Node.js installed)

```bash
cd server
npm install
npx wrangler login          # opens browser, login with Cloudflare
npm run deploy              # prints the wss:// URL
```

### Free tier limits

Cloudflare Workers free tier: 100,000 requests/day, unlimited WebSocket connections. Plenty for a friend-group voice chat — each client uses ~1 request/sec while signaling, then drops to direct P2P.

---

## Set up release signing (one-time, optional but recommended)

The first build is debug-signed (works fine for testing, but every install will be marked "unknown developer"). To make it officially Rajat-signed so updates are seamless:

1. Go to **https://github.com/rajatxo/MicUp/settings/secrets/actions**
2. Add these 4 secrets (same ones used for Coral-X, per the handover doc):

   | Secret name | Value |
   |---|---|
   | `CORAL_X_KEYSTORE_BASE64` | base64 of your `micup-release.jks` file |
   | `CORAL_X_STORE_PASSWORD` | `The Weeknd 199` |
   | `CORAL_X_KEY_ALIAS` | `coralx` |
   | `CORAL_X_KEY_PASSWORD` | `The Weeknd 199` |

3. To get the base64 of your keystore:
   ```bash
   base64 -w 0 micup-release.jks > keystore.b64
   ```
   (You can reuse the same `coral-x-release.jks` from Coral-X — same keystore, same alias, per the handover doc.)

4. Push any commit. The next build will be properly release-signed.

---

## Coral X layout laws applied

This app follows all 10 Universal Layout Laws from the Coral X foundation:

- **Law 1**: ONE CompositionLocal for ALL insets (`LocalPlayerAwareWindowInsets`)
- **Law 2**: Manual edge-to-edge via `WindowCompat.setDecorFitsSystemWindows(false)` — NOT `enableEdgeToEdge()`
- **Law 3**: Theme XML with `enforceStatusBarContrast=false` + `enforceNavigationBarContrast=false`
- **Law 4**: (N/A — no bottom sheets in v1)
- **Law 5**: (N/A — no nested scroll in v1)
- **Law 6**: System gesture exclusion zone on the left 35%
- **Law 7**: `BoxWithConstraints` at root, never `screenHeightDp`
- **Law 8**: Manifest flags — `enableOnBackInvokedCallback`, `windowSoftInputMode`, full `configChanges`, `singleTask`
- **Law 9**: Status bar icon appearance flips per theme
- **Law 10**: `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` for immersive mode

## Tech stack

- **Kotlin 2.2.20** + Jetpack Compose (Material 3)
- **AGP 8.7.2**, minSdk 24, targetSdk 35
- **WebRTC**: `io.getstream:stream-webrtc-android:1.3.9` (Stream's maintained distribution of Google's libwebrtc)
- **Signaling**: OkHttp WebSocket → Cloudflare Worker
- **Serialization**: kotlinx.serialization (sealed `SignalMessage` interface)
- **Coroutines**: kotlinx.coroutines
- **Glass morphism**: `io.github.kyant0:backdrop:1.0.0`
- **Design**: pure black AMOLED, coral `#FF6B6B` accent, glass cards

## Project structure

```
app/src/main/java/com/rajatxo/micup/
├── MicUpApp.kt              # Application — WebRTC init, notif channel
├── MainActivity.kt          # Single Activity — Laws 2/6/9/10
├── audio/
│   ├── AudioConfig.kt       # No-ducking config (USAGE_GAME + MODE_NORMAL)
│   ├── AudioDeviceModuleBuilder.java  # Java helper (Kotlin metadata workaround)
│   ├── VoiceEngine.kt       # PeerConnectionFactory + local audio track
│   ├── RoomClient.kt        # Signaling + peer connection mesh
│   ├── RoomViewModel.kt     # Bridges RoomClient with Compose
│   ├── SignalingClient.kt   # OkHttp WebSocket wrapper
│   └── VoiceService.kt      # Foreground service keeps mic alive while gaming
├── ui/
│   ├── LocalWindowInsets.kt # Law 1
│   ├── MicUpScaffold.kt     # Laws 1 + 7, NavHost
│   ├── components/GlassCard.kt
│   ├── screens/
│   │   ├── HomeScreen.kt    # Create / Join room
│   │   ├── RoomScreen.kt    # In-room UI (mute, leave, participants)
│   │   └── SettingsScreen.kt
│   └── theme/                # Color, Type, Theme
server/
├── worker.js                # Cloudflare Worker signaling server
├── wrangler.toml
└── package.json
.github/workflows/build.yml  # Build + sign + upload APK artifact
```

## Roadmap (post-v1)

- [ ] TURN server support (for symmetric NATs — some carrier networks)
- [ ] Push-to-talk mode
- [ ] Per-participant volume control
- [ ] Noise gate threshold setting
- [ ] Cal Sans bundled font
- [ ] Custom Lucide-style icons (currently using Material Icons)
- [ ] Lottie animations for the room-state transitions
- [ ] Bump AGP to 8.13.2 (fixes the lint Kotlin 2.2.20 incompatibility properly)

---

Made with love in India 🇮🇳

Developer: https://t.me/rajat_xo · Support: https://t.me/coralmusicsupport
