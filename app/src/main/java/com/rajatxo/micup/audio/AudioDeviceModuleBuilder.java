package com.rajatxo.micup.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import org.webrtc.audio.JavaAudioDeviceModule;

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
 * This is the exact technique Parallel: Your Hangout Place uses:
 * "By utilizing media sound controls instead of traditional call audio,
 * it enhances the overall gaming experience."
 */
public final class AudioDeviceModuleBuilder {

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
        JavaAudioDeviceModule.Builder builder = JavaAudioDeviceModule.builder(context)
                .setSampleRate(48_000)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setUseStereoOutput(true)
                // THE MAGIC: USAGE_GAME audio attributes → no ducking.
                .setAudioAttributes(NO_DUCKING_ATTRIBUTES);
        if (samplesCallback != null) {
            builder.setSamplesReadyCallback(samplesCallback);
        }
        return builder.createAudioDeviceModule();
    }

    /**
     * Enable loudspeaker + force MODE_NORMAL (the no-ducking trick).
     *
     * CRITICAL: We must stay in MODE_NORMAL. If we enter
     * MODE_IN_COMMUNICATION (which WebRTC does by default), Android
     * will duck other apps. By forcing MODE_NORMAL + USAGE_GAME,
     * the OS treats us as a game sound effect, not a phone call.
     */
    public static void enableLoudspeakerAndNoDucking(Context context) {
        try {
            android.media.AudioManager am = (android.media.AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                // Enable loudspeaker (bottom speaker, not earpiece)
                am.setSpeakerphoneOn(true);
                // CRITICAL: Stay in MODE_NORMAL — do NOT enter
                // MODE_IN_COMMUNICATION. This is what prevents ducking.
                am.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Aggressively force loudspeaker on. Called when ICE connects.
     * Some phones need this called AFTER the audio track is active
     * for the routing to take effect.
     */
    public static void forceLoudspeakerOn(Context context) {
        try {
            android.media.AudioManager am = (android.media.AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setSpeakerphoneOn(false);
                am.setSpeakerphoneOn(true);
                am.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Release the AudioDeviceModule. Safe to call on null.
     */
    public static void release(Object adm) {
        if (adm instanceof JavaAudioDeviceModule) {
            ((JavaAudioDeviceModule) adm).release();
        }
    }
}
