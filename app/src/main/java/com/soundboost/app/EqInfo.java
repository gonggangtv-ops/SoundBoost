package com.soundboost.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;

/** อ่านข้อมูลอีควอไลเซอร์ของเครื่อง (จำนวนช่อง ความถี่ ช่วงที่ปรับได้) */
public final class EqInfo {
    public final int bands;
    public final int minLevel;   // มิลลิเบล
    public final int maxLevel;   // มิลลิเบล
    public final int[] freqHz;

    private EqInfo(int bands, int min, int max, int[] freq) {
        this.bands = bands; this.minLevel = min; this.maxLevel = max; this.freqHz = freq;
    }

    private static EqInfo cached;

    public static synchronized EqInfo get(Context c) {
        if (cached != null) return cached;
        Equalizer eq = null;
        try {
            int session = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).generateAudioSessionId();
            eq = new Equalizer(0, session);
        } catch (Throwable t1) {
            try { eq = new Equalizer(0, 0); } catch (Throwable ignored) { }
        }
        if (eq != null) {
            try {
                short n = eq.getNumberOfBands();
                short[] r = eq.getBandLevelRange();
                int[] f = new int[n];
                for (short i = 0; i < n; i++) f[i] = eq.getCenterFreq(i) / 1000;
                cached = new EqInfo(n, r[0], r[1], f);
            } catch (Throwable ignored) {
            } finally {
                eq.release();
            }
        }
        if (cached == null) {
            // ค่าทั่วไปของ Android ถ้าอ่านจากเครื่องไม่ได้
            cached = new EqInfo(5, -1500, 1500, new int[]{60, 230, 910, 3600, 14000});
        }
        return cached;
    }

    public int clamp(int mB) { return Math.max(minLevel, Math.min(maxLevel, mB)); }

    public static String label(int hz) {
        return hz >= 1000 ? (hz % 1000 == 0 ? (hz / 1000) + " kHz" : String.format("%.1f kHz", hz / 1000f)) : hz + " Hz";
    }
}
