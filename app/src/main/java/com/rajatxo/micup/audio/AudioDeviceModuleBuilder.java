package com.rajatxo.micup.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaRecorder;

import org.webrtc.audio.JavaAudioDeviceModule;

/**
 * Java helper that builds the WebRTC AudioDeviceModule with our
 * no-ducking audio attributes.
 *
 * Why Java and not Kotlin? Kotlin's compiler had a metadata-resolution
 * issue with the JavaAudioDeviceModule class inside stream-webrtc-android
 * 1.3.9 — Java resolves the class via the regular classpath and doesn't
 * care about the .kotlin_module index. Same final bytecode, no Kotlin
 * metadata lookup needed.
 *
 * The CRITICAL settings are here:
 *  - useHardwareAcousticEchoCanceler = false  (we use WebRTC's AEC3)
 *  - useHardwareNoiseSuppressor     = false  (we use WebRTC's NS)
 *  - audioAttributes = USAGE_GAME (prevents OS ducking other apps)
 *  - audioSource = VOICE_COMMUNICATION (Android mic tag, not a usage tag)
 */
public final class AudioDeviceModuleBuilder {

    private AudioDeviceModuleBuilder() { }

    public static JavaAudioDeviceModule build(Context context) {
        // Force MODE_NORMAL — never let the OS promote us into
        // MODE_IN_COMMUNICATION which triggers ducking.
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        return JavaAudioDeviceModule.builder(context)
                .setSampleRate(48_000)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setUseStereoOutput(true)
                .setAudioAttributes(attrs)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .createAudioDeviceModule();
    }
}
