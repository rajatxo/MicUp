package com.rajatxo.micup.audio;

import android.content.Context;
import org.webrtc.audio.JavaAudioDeviceModule;

/**
 * Java helper to build the WebRTC AudioDeviceModule.
 *
 * WHY JAVA, NOT KOTLIN:
 * Kotlin's compiler has a metadata-resolution issue with
 * JavaAudioDeviceModule inside stream-webrtc-android 1.3.9 —
 * the org.webrtc.audio subpackage's .kotlin_module isn't being
 * indexed properly, so 'import org.webrtc.audio.JavaAudioDeviceModule'
 * fails with 'Unresolved reference' in Kotlin.
 *
 * Java resolves the class via the standard classpath without
 * going through Kotlin's metadata lookup, sidestepping the issue.
 *
 * Same pattern we used in MicUp v1.x which worked.
 */
public final class AudioDeviceModuleBuilder {

    private AudioDeviceModuleBuilder() { }

    /**
     * Build the AudioDeviceModule with software AEC/NS (not hardware).
     *
     * @param samplesCallback optional — pass null if you don't need
     *   raw PCM samples (e.g. Step 5 SDP test doesn't need them).
     */
    public static JavaAudioDeviceModule build(
            Context context,
            JavaAudioDeviceModule.SamplesReadyCallback samplesCallback) {
        JavaAudioDeviceModule.Builder builder = JavaAudioDeviceModule.builder(context)
                .setSampleRate(48_000)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setUseStereoOutput(true);
        if (samplesCallback != null) {
            builder.setSamplesReadyCallback(samplesCallback);
        }
        return builder.createAudioDeviceModule();
    }

    /**
     * Release the AudioDeviceModule. Safe to call on null.
     * Wrapped in try/catch by the caller.
     */
    public static void release(Object adm) {
        if (adm instanceof JavaAudioDeviceModule) {
            ((JavaAudioDeviceModule) adm).release();
        }
    }
}
