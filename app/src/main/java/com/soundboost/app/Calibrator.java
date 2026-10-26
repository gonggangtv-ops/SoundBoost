package com.soundboost.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

/**
 * วัดลำโพงด้วยไมค์มือถือ: อัดเสียงห้องเงียบ 1 วินาที แล้วเล่น pink noise 6 วินาทีพร้อมอัด
 * ต้องได้สิทธิ์ใช้ไมค์ก่อนเรียก run()
 */
final class Calibrator {
    interface Progress { void onStep(String text); }

    private static final int R = CalibMath.RATE;
    private static final int NOISE_SEC = 6;

    static CalibMath.Result run(Context c, Progress progress) {
        AudioRecord rec = null;
        AudioTrack track = null;
        try {
            int minRec = AudioRecord.getMinBufferSize(R, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(pickSource(c), R, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minRec, R / 5 * 2));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) return fail("เปิดไมค์ไม่ได้ ปิดแอปอื่นที่ใช้ไมค์อยู่แล้วลองใหม่");
            preferBuiltInMic(c, rec);

            short[] noise = CalibMath.pinkNoise(R * NOISE_SEC, System.nanoTime());
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(R).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(noise.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(noise, 0, noise.length);

            rec.startRecording();

            // 1) ฟังเสียงรบกวนในห้อง
            progress.onStep("กำลังฟังเสียงในห้อง… อยู่เงียบๆ ก่อนนะ");
            short[] ambient = new short[R];
            readFully(rec, new short[R / 4]);          // ทิ้งช่วงแรกที่ไมค์เพิ่งเปิด
            readFully(rec, ambient);

            // 2) เล่นเสียงทดสอบพร้อมอัด
            progress.onStep("กำลังเล่นเสียงทดสอบ… ประมาณ 6 วินาที");
            short[] captured = new short[R * (NOISE_SEC + 1)];
            track.play();
            readFully(rec, captured);

            progress.onStep("กำลังคำนวณ…");
            double peak = 0;
            // เว้นช่วงต้น 1.5 วินาที (บลูทูธหน่วงเสียงได้ถึงครึ่งวินาที) และท้ายก่อนเสียงจบ
            int from = R * 3 / 2, to = R * NOISE_SEC;
            for (int i = from; i < to; i++) peak = Math.max(peak, Math.abs(captured[i]) / 32768.0);

            double[] ref = CalibMath.bandLevels(noise, R, R * (NOISE_SEC - 1));
            double[] measured = CalibMath.bandLevels(captured, from, to);
            double[] amb = CalibMath.bandLevels(ambient, 0, ambient.length);
            return CalibMath.analyze(ref, measured, amb, peak);
        } catch (SecurityException e) {
            return fail("ยังไม่ได้อนุญาตให้ใช้ไมค์");
        } catch (Throwable t) {
            return fail("วัดไม่สำเร็จ: " + t.getMessage());
        } finally {
            if (track != null) try { track.stop(); track.release(); } catch (Throwable ignored) { }
            if (rec != null) try { rec.stop(); rec.release(); } catch (Throwable ignored) { }
        }
    }

    /** ใช้ไมค์แบบไม่ผ่านการแต่งเสียงถ้าเครื่องรองรับ ผลวัดจะแม่นกว่า */
    private static int pickSource(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if ("true".equals(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED))) {
            return MediaRecorder.AudioSource.UNPROCESSED;
        }
        return MediaRecorder.AudioSource.VOICE_RECOGNITION;
    }

    /** บังคับใช้ไมค์ของมือถือ ไม่ใช่ไมค์บนลำโพงบลูทูธ */
    private static void preferBuiltInMic(Context c, AudioRecord rec) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) { rec.setPreferredDevice(d); return; }
            }
        } catch (Throwable ignored) { }
    }

    private static void readFully(AudioRecord rec, short[] buf) {
        int off = 0;
        while (off < buf.length) {
            int n = rec.read(buf, off, buf.length - off);
            if (n <= 0) throw new IllegalStateException("อ่านเสียงจากไมค์ไม่ได้");
            off += n;
        }
    }

    private static CalibMath.Result fail(String msg) {
        CalibMath.Result r = new CalibMath.Result();
        r.error = msg;
        return r;
    }
}
