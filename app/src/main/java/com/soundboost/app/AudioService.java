package com.soundboost.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

/**
 * ทำงานเบื้องหลังเพื่อคุมเอฟเฟกต์เสียง
 * 1) พยายามใส่เอฟเฟกต์กับเสียงทั้งเครื่อง (session 0)
 * 2) รับสัญญาณจากแอปเพลงที่บอกว่ากำลังเล่น (เช่น Spotify, YouTube Music บางเวอร์ชัน)
 *    แล้วใส่เอฟเฟกต์ให้แอปนั้นโดยตรง ซึ่งได้ผลแน่นอนกว่า
 */
public class AudioService extends Service {
    public static volatile AudioService instance;

    private static final String CHANNEL = "soundboost";
    private static final String ACTION_STOP = "com.soundboost.app.STOP";
    private static final int PRIORITY = 1000;

    private final Map<Integer, EffectSet> sessions = new HashMap<>();
    private boolean globalOk = false;
    private Prefs prefs;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int s = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            if (s <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
                openSession(s);
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
                closeSession(s);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        try {
            goForeground();
        } catch (Throwable t) {
            stopSelf();
            return;
        }
        instance = this;

        EffectSet global = new EffectSet(0);
        globalOk = global.works();
        if (globalOk) sessions.put(0, global); else global.release();

        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);

        applyAll();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        instance = null;
        try { unregisterReceiver(receiver); } catch (Throwable ignored) { }
        synchronized (sessions) {
            for (EffectSet e : sessions.values()) e.release();
            sessions.clear();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ---------- สำหรับหน้าจอหลัก ----------

    public boolean isGlobalOk() { return globalOk; }

    public int appSessionCount() {
        synchronized (sessions) { return sessions.size() - (globalOk ? 1 : 0); }
    }

    /** มีโหมด MAX ใช้ได้ในเครื่องนี้ไหม */
    public boolean maxSupported() {
        synchronized (sessions) {
            for (EffectSet e : sessions.values()) if (e.max != null) return true;
        }
        return false;
    }

    public void applyAll() {
        EqInfo info = EqInfo.get(this);
        synchronized (sessions) {
            for (EffectSet e : sessions.values()) e.apply(prefs, info);
        }
    }

    // ---------- ภายใน ----------

    private void openSession(int s) {
        synchronized (sessions) {
            if (sessions.containsKey(s)) return;
            EffectSet e = new EffectSet(s);
            if (e.works()) { sessions.put(s, e); e.apply(prefs, EqInfo.get(this)); }
            else e.release();
        }
    }

    private void closeSession(int s) {
        synchronized (sessions) {
            EffectSet e = sessions.remove(s);
            if (e != null) e.release();
        }
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "ปรับเสียงทำงานอยู่", NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AudioService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_wave)
                .setContentTitle("SoundBoost กำลังปรับเสียง")
                .setContentText("แตะเพื่อเปิดการตั้งค่า")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "ปิด", stop).build())
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
    }

    /** ชุดเอฟเฟกต์ของเสียง 1 ช่อง: EQ + เพิ่มเบส + เพิ่มความดัง */
    private static final class EffectSet {
        Equalizer eq;
        BassBoost bb;
        LoudnessEnhancer le;
        MaxEngine max;

        EffectSet(int session) {
            if (Build.VERSION.SDK_INT >= 28) max = MaxEngine.create(PRIORITY, session);
            try { eq = new Equalizer(PRIORITY, session); } catch (Throwable ignored) { }
            try { bb = new BassBoost(PRIORITY, session); } catch (Throwable ignored) { }
            try { le = new LoudnessEnhancer(session); } catch (Throwable ignored) { }
        }

        boolean works() { return eq != null || bb != null || le != null || max != null; }

        void apply(Prefs p, EqInfo info) {
            if (eq != null) try {
                short n = eq.getNumberOfBands();
                for (short i = 0; i < n; i++) eq.setBandLevel(i, (short) info.clamp(p.band(i)));
                eq.setEnabled(true);
            } catch (Throwable ignored) { }

            if (bb != null) try {
                int v = p.bass();
                if (v > 0 && bb.getStrengthSupported()) bb.setStrength((short) v);
                bb.setEnabled(v > 0);
            } catch (Throwable ignored) { }

            if (le != null) try {
                // ถ้าโหมด MAX ทำงานอยู่ ให้ MAX คุมความดังแทน จะได้ไม่ดันซ้อนกันจนแตก
                int v = (max != null && p.maxOn()) ? 0 : p.loud();
                le.setTargetGain(v);
                le.setEnabled(v > 0);
            } catch (Throwable ignored) { }

            if (max != null) max.apply(p);
        }

        void release() {
            try { if (eq != null) eq.release(); } catch (Throwable ignored) { }
            try { if (bb != null) bb.release(); } catch (Throwable ignored) { }
            try { if (le != null) le.release(); } catch (Throwable ignored) { }
            if (max != null) max.release();
        }
    }
}
