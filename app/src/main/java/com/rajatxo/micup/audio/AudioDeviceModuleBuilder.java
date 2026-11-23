package com.rajatxo.micup.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.lang.reflect.Field;

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
 * ROUTING FIX (some phones put voice in the earpiece):
 * - Use MIC, not VOICE_COMMUNICATION. A "communication" recording source
 *   makes some OEMs route the output to the earpiece.
 * - Select the built-in speaker directly on WebRTC's AudioTrack with
 *   setPreferredDevice(). setSpeakerphoneOn() is ignored in MODE_NORMAL
 *   on many devices, which is why it alone was unreliable.
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
        // the first moment we can select the speaker on the real track.
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
                        forceSpeaker(context, holder[0]);
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
     * Enable loudspeaker + force MODE_NORMAL (the no-ducking trick).
     *
     * CRITICAL: We must stay in MODE_NORMAL. If we enter
     * MODE_IN_COMMUNICATION (which WebRTC does by default), Android
     * will duck other apps.
     */
    public static void enableLoudspeakerAndNoDucking(Context context) {
        forceSpeaker(context, null);
    }

    /**
     * Force output to the built-in loudspeaker without entering
     * MODE_IN_COMMUNICATION.
     *
     * Safe to call before the AudioTrack exists (adm null): it still sets
     * normal mode + speakerphone, and the track-start callback calls it
     * again once the real track is available.
     */
    public static void forceSpeaker(Context context, JavaAudioDeviceModule adm) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;

            // Keep normal mode: MODE_IN_COMMUNICATION would duck game audio.
            am.setMode(AudioManager.MODE_NORMAL);

            // Let Bluetooth/wired headsets win if the user has one connected.
            // shortcut: a headset plugged in mid-call is picked up on the next
            // forceSpeaker() call (ICE/track start), not instantly.
            if (hasHeadset(am)) {
                // Drop any speaker preference so the system can route to the headset.
                AudioTrack track = extractAudioTrack(adm);
                if (track != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track.setPreferredDevice(null);
                }
                return;
            }
            am.setSpeakerphoneOn(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

            AudioDeviceInfo speaker = findBuiltinSpeaker(am);
            AudioTrack track = extractAudioTrack(adm);
            if (speaker != null && track != null) {
                boolean ok = track.setPreferredDevice(speaker);
                Log.i(TAG, "setPreferredDevice(speaker)=" + ok);
            }
        } catch (Throwable t) {
            Log.w(TAG, "forceSpeaker failed", t);
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

    private static AudioDeviceInfo findBuiltinSpeaker(AudioManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                return d;
            }
        }
        return null;
    }

    private static boolean hasHeadset(AudioManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = d.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                return true;
            }
        }
        return false;
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
