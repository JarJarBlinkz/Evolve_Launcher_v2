package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.media.AudioManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class QuickSettingsManager {

    private final Context context;
    private final AudioManager audioManager;

    public QuickSettingsManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // ===== VOLUME - This works on Quest =====
    public void setVolume(int progress, SeekBar seekBar, TextView valueText) {
        try {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volume = (progress * maxVolume) / 100;

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            valueText.setText(progress + "%");

        } catch (Exception e) {
            Toast.makeText(context, "Cannot change volume", Toast.LENGTH_SHORT).show();
        }
    }

    public int getCurrentVolume() {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return (currentVolume * 100) / maxVolume;
    }
}