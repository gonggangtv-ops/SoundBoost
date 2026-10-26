package com.soundboost.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** เล่นเสียงทดสอบความถี่ต่ำ เปลี่ยนความถี่ได้ขณะเล่น (ใช้วัดว่าลำโพงเล่นเบสได้ต่ำแค่ไหน) */
final class ToneGen {
    private static final int RATE = 48000;
    private static final float VOLUME = 0.35f;   // ไม่ดังเกินไป กันลำโพงเสียหาย

    private volatile float freq = 100f;
    private volatile boolean running;
    private Thread thread;

    void setFreq(float hz) { freq = hz; }
    boolean isPlaying() { return running; }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "tone");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private void loop() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Throwable t) {
            running = false;
            return;
        }

        short[] buf = new short[1024];
        double phase = 0;
        float level = 0f;   // ค่อยๆ ดังขึ้น/เบาลง จะได้ไม่มีเสียงป๊อก
        track.play();
        while (running) {
            double step = 2 * Math.PI * freq / RATE;
            for (int i = 0; i < buf.length; i++) {
                level = Math.min(1f, level + 0.0005f);
                buf[i] = (short) (Math.sin(phase) * VOLUME * level * Short.MAX_VALUE);
                phase += step;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
            }
            track.write(buf, 0, buf.length);
        }
        // fade out
        for (int i = 0; i < buf.length; i++) {
            level = Math.max(0f, level - 0.002f);
            buf[i] = (short) (Math.sin(phase) * VOLUME * level * Short.MAX_VALUE);
            phase += 2 * Math.PI * freq / RATE;
        }
        track.write(buf, 0, buf.length);
        try { track.stop(); } catch (Throwable ignored) { }
        track.release();
    }
}
