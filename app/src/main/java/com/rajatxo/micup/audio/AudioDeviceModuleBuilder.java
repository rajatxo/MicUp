package com.rajatxo.micup.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Java helper to build the WebRTC AudioDeviceModule with NO-DUCKING config.
 *
 * THE MAGIC (the whole reason this app exists):
 *
 * By default, WebRTC uses USAGE_VOICE_COMMUNICATION audio attributes.
 * Android sees this as a phone call → enters MODE_IN_COMMUNICATION →
 * DUCKS every other audio stream (gun sounds, footsteps, music) to ~20%.
 *
 * We override this with USAGE_GAME + CONTENT_TYPE_SPEECH. Android now
 * treats our voice chat as a GAME SOUND EFFECT → stays in MODE_NORMAL →
 * NO DUCKING. Squad voice + game audio both play at full volume.
 *
 * ROUTING:
 * - Pick the best connected output: wired headset > USB > BLE > Bluetooth
 *   A2DP > Bluetooth SCO > built-in speaker.
 * - Select that device directly on WebRTC's AudioTrack with
 *   setPreferredDevice(). setSpeakerphoneOn() alone is ignored in
 *   MODE_NORMAL on many devices.
 * - Use MIC, not VOICE_COMMUNICATION, so some OEMs don't take the
 *   communication/earpiece route.
 *
 * Bluetooth note: A2DP is output-only. To keep game audio un-ducked, the
 * mic stays on the phone. A wired headset uses its own mic automatically.
 */
public final class AudioDeviceModuleBuilder {

    private static final String TAG = "MicUp/AudioRoute";

    private AudioDeviceModuleBuilder() { }

    /**
     * The no-ducking audio attributes.
     * USAGE_GAME = OS treats us as a game sound → no ducking.
     * CONTENT_TYPE_SPEECH = optimized for voice clarity.
     */
    private static final AudioAttributes NO_DUCKING_ATTRIBUTES =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    /**
     * Build the AudioDeviceModule with NO-DUCKING audio attributes.
     *
     * @param samplesCallback optional — pass null if you don't need
     *   raw PCM samples.
     */
    public static JavaAudioDeviceModule build(
            Context context,
            JavaAudioDeviceModule.SamplesReadyCallback samplesCallback) {
        // The callback fires after the output AudioTrack is playing. That is
        // the first moment we can select the output device on the real track.
        final JavaAudioDeviceModule[] holder = new JavaAudioDeviceModule[1];

        JavaAudioDeviceModule.Builder builder = JavaAudioDeviceModule.builder(context)
                .setSampleRate(48_000)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setUseStereoOutput(true)
                // MIC avoids the "communication" audio path on OEMs that
                // otherwise force the earpiece. WebRTC's software AEC3 still runs.
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                // THE MAGIC: USAGE_GAME audio attributes → no ducking.
                .setAudioAttributes(NO_DUCKING_ATTRIBUTES)
                .setAudioTrackStateCallback(new JavaAudioDeviceModule.AudioTrackStateCallback() {
                    @Override
                    public void onWebRtcAudioTrackStart() {
                        applyAudioRoute(context, holder[0]);
                    }

                    @Override
                    public void onWebRtcAudioTrackStop() { }
                });

        if (samplesCallback != null) {
            builder.setSamplesReadyCallback(samplesCallback);
        }

        JavaAudioDeviceModule adm = builder.createAudioDeviceModule();
        holder[0] = adm;
        return adm;
    }

    /**
     * Apply the no-ducking mode + the best available output route.
     * Safe to call before the AudioTrack exists (adm null): it still sets
     * normal mode + speakerphone; the track-start callback calls it again.
     */
    public static void enableLoudspeakerAndNoDucking(Context context) {
        applyAudioRoute(context, null);
    }

    /**
     * Route voice to the best connected device without entering
     * MODE_IN_COMMUNICATION (which would duck game audio).
     *
     * Priority: wired headset > USB > BLE > Bluetooth A2DP > Bluetooth SCO
     * > built-in speaker.
     */
    public static void applyAudioRoute(Context context, JavaAudioDeviceModule adm) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;

            AudioDeviceInfo device = prepareRoute(am);
            if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

            AudioTrack track = extractAudioTrack(adm);
            if (track != null) {
                boolean ok = track.setPreferredDevice(device);
                Log.i(TAG, "route type=" + device.getType() + " preferred=" + ok);
            }
            applyInputRoute(am, adm);
        } catch (Throwable t) {
            Log.w(TAG, "applyAudioRoute failed", t);
        }
    }

    /** Undo the forced route when leaving the room. */
    public static void clearPreferredDevice(JavaAudioDeviceModule adm) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            AudioTrack track = extractAudioTrack(adm);
            if (track != null) {
                track.setPreferredDevice(null);
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Plays a short two-beep tone through the same USAGE_GAME route as the
     * voice call. Lets one person check speaker / wired / Bluetooth routing
     * without a second phone.
     */
    public static void playTestTone(Context context) {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                AudioDeviceInfo device = prepareRoute(am);

                int sampleRate = 48_000;
                int channelMask = AudioFormat.CHANNEL_OUT_MONO;
                int minBuffer = AudioTrack.getMinBufferSize(
                        sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
                int bufferSize = Math.max(minBuffer > 0 ? minBuffer : 0, sampleRate);

                track = createTestTrack(sampleRate, channelMask, bufferSize);
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "test tone track not initialized");
                    return;
                }
                if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track.setPreferredDevice(device);
                }

                short[] beep = makeBeep(sampleRate);
                short[] gap = new short[beep.length];
                track.play();
                track.write(beep, 0, beep.length);
                track.write(gap, 0, gap.length);
                track.write(beep, 0, beep.length);
                track.stop();
            } catch (Throwable t) {
                Log.w(TAG, "test tone failed", t);
            } finally {
                if (track != null) {
                    try { track.release(); } catch (Throwable ignored) { }
                }
            }
        }, "MicUp-TestTone").start();
    }

    /**
     * Records ~2 seconds from the mic and plays it back through the same
     * USAGE_GAME route. Lets one person confirm the mic works (and see the
     * Android mic-in-use indicator) without a second phone.
     *
     * Only call from the join screen: while in a call WebRTC already owns an
     * AudioRecord, and a second capture can conflict.
     */
    public static void playMicTest(Context context) {
        new Thread(() -> {
            AudioRecord record = null;
            AudioTrack track = null;
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                AudioDeviceInfo device = prepareRoute(am);

                int sampleRate = 48_000;
                int minRec = AudioRecord.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minRec <= 0) {
                    Log.w(TAG, "mic test: bad record buffer size");
                    return;
                }
                record = createTestRecord(sampleRate, Math.max(minRec, sampleRate));
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "mic test: AudioRecord not initialized (permission?)");
                    return;
                }

                int samples = sampleRate * 2; // 2 seconds
                short[] captured = new short[samples];
                record.startRecording();
                int offset = 0;
                while (offset < samples) {
                    int n = record.read(captured, offset, samples - offset);
                    if (n <= 0) break;
                    offset += n;
                }
                record.stop();

                if (offset == 0) {
                    Log.w(TAG, "mic test: captured nothing");
                    return;
                }

                int minPlay = AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = createTestTrack(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                        Math.max(minPlay > 0 ? minPlay : 0, sampleRate));
                if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
                if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track.setPreferredDevice(device);
                }
                track.play();
                track.write(captured, 0, offset);
                track.stop();
            } catch (Throwable t) {
                Log.w(TAG, "mic test failed", t);
            } finally {
                if (record != null) {
                    try { record.release(); } catch (Throwable ignored) { }
                }
                if (track != null) {
                    try { track.release(); } catch (Throwable ignored) { }
                }
            }
        }, "MicUp-MicTest").start();
    }

    private static AudioRecord createTestRecord(int sampleRate, int bufferSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            return new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        }
        return new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
    }

    private static AudioDeviceInfo prepareRoute(AudioManager am) {
        // Keep normal mode: MODE_IN_COMMUNICATION would duck game audio.
        am.setMode(AudioManager.MODE_NORMAL);
        AudioDeviceInfo device = pickOutputDevice(am);
        if (device != null) {
            am.setSpeakerphoneOn(device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        } else {
            // API < 23 has no device list; keep the old speakerphone fallback.
            am.setSpeakerphoneOn(true);
        }
        return device;
    }

    private static AudioDeviceInfo pickOutputDevice(AudioManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;

        AudioDeviceInfo wiredHeadset = null, wiredPhones = null;
        AudioDeviceInfo usbHeadset = null, usbDevice = null;
        AudioDeviceInfo bleHeadset = null, bleSpeaker = null, hearingAid = null;
        AudioDeviceInfo a2dp = null, sco = null, speaker = null;

        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    if (wiredHeadset == null) wiredHeadset = d;
                    break;
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    if (wiredPhones == null) wiredPhones = d;
                    break;
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (usbHeadset == null) usbHeadset = d;
                    break;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                    if (usbDevice == null) usbDevice = d;
                    break;
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                    if (bleHeadset == null) bleHeadset = d;
                    break;
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                    if (bleSpeaker == null) bleSpeaker = d;
                    break;
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    if (hearingAid == null) hearingAid = d;
                    break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    if (a2dp == null) a2dp = d;
                    break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    if (sco == null) sco = d;
                    break;
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    speaker = d;
                    break;
                default:
                    break;
            }
        }

        // Wired/USB first: two-way audio works without changing the mode.
        if (wiredHeadset != null) return wiredHeadset;
        if (wiredPhones != null) return wiredPhones;
        if (usbHeadset != null) return usbHeadset;
        if (usbDevice != null) return usbDevice;
        // LE Audio / hearing aids, then classic Bluetooth.
        if (bleHeadset != null) return bleHeadset;
        if (hearingAid != null) return hearingAid;
        if (bleSpeaker != null) return bleSpeaker;
        if (a2dp != null) return a2dp;
        if (sco != null) return sco;
        return speaker;
    }

    /**
     * Wired/USB/BLE headsets have their own mic. Select it so MIC source
     * doesn't keep using the phone mic. Bluetooth SCO is skipped on purpose:
     * activating SCO is what causes game audio to duck.
     */
    private static void applyInputRoute(AudioManager am, JavaAudioDeviceModule adm) {
        if (adm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        AudioDeviceInfo input = pickInputDevice(am);
        try {
            Field inputField = JavaAudioDeviceModule.class.getField("audioInput");
            Object audioInput = inputField.get(adm);
            if (audioInput == null) return;
            Method setPreferred = audioInput.getClass()
                    .getDeclaredMethod("setPreferredDevice", AudioDeviceInfo.class);
            setPreferred.setAccessible(true);
            // null clears a stale headset preference after unplugging.
            setPreferred.invoke(audioInput, input);
            Log.i(TAG, "input type=" + (input != null ? String.valueOf(input.getType()) : "default"));
        } catch (Throwable t) {
            // Reflection is best-effort; the default mic still works.
            Log.w(TAG, "applyInputRoute failed", t);
        }
    }

    private static AudioDeviceInfo pickInputDevice(AudioManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        AudioDeviceInfo wired = null, usb = null, ble = null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    if (wired == null) wired = d;
                    break;
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (usb == null) usb = d;
                    break;
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                    if (ble == null) ble = d;
                    break;
                default:
                    break;
            }
        }
        if (wired != null) return wired;
        if (usb != null) return usb;
        if (ble != null) return ble;
        return null;
    }

    private static AudioTrack createTestTrack(int sampleRate, int channelMask, int bufferSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build();
            return new AudioTrack.Builder()
                    .setAudioAttributes(NO_DUCKING_ATTRIBUTES)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        }
        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
    }

    private static short[] makeBeep(int sampleRate) {
        int samples = sampleRate / 4; // 250 ms
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            double fade = Math.min(1.0, Math.min(i, samples - 1 - i) / (sampleRate * 0.01));
            out[i] = (short) (Math.sin(2 * Math.PI * 660.0 * i / sampleRate)
                    * 0.35 * Short.MAX_VALUE * fade);
        }
        return out;
    }

    /**
     * shortcut: WebRTC's JavaAudioDeviceModule does not expose its AudioTrack,
     * so we read it reflectively. Pin stream-webrtc-android; if the field moves,
     * this returns null and the setSpeakerphoneOn fallback still applies.
     */
    private static AudioTrack extractAudioTrack(JavaAudioDeviceModule adm) {
        if (adm == null) return null;
        try {
            Field outputField = JavaAudioDeviceModule.class.getField("audioOutput");
            Object output = outputField.get(adm);
            if (output == null) return null;

            Field trackField = output.getClass().getDeclaredField("audioTrack");
            trackField.setAccessible(true);
            Object track = trackField.get(output);
            return track instanceof AudioTrack ? (AudioTrack) track : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
