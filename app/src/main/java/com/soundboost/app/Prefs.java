package com.soundboost.app;

import android.content.Context;
import android.content.SharedPreferences;

/** เก็บค่าที่ผู้ใช้ตั้งไว้ (ค่า EQ เป็นมิลลิเบล: 100 = 1 dB) */
public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("soundboost", Context.MODE_PRIVATE);
    }

    public boolean enabled() { return sp.getBoolean("enabled", false); }
    public void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    public int band(int i) { return sp.getInt("band" + i, 0); }
    public void setBand(int i, int mB) { sp.edit().putInt("band" + i, mB).apply(); }

    /** เพิ่มเบส 0..1000 */
    public int bass() { return sp.getInt("bass", 0); }
    public void setBass(int v) { sp.edit().putInt("bass", v).apply(); }

    /** เพิ่มความดัง 0..1500 มิลลิเบล (0..15 dB) */
    public int loud() { return sp.getInt("loud", 0); }
    public void setLoud(int v) { sp.edit().putInt("loud", v).apply(); }

    // ---------- โหมด MAX ----------
    public boolean maxOn() { return sp.getBoolean("maxOn", false); }
    public void setMaxOn(boolean v) { sp.edit().putBoolean("maxOn", v).apply(); }

    /** แรงขับ 0..12 dB (ลิมิตเตอร์กันไม่ให้แตก) */
    public int drive() { return sp.getInt("drive", 4); }
    public void setDrive(int v) { sp.edit().putInt("drive", v).apply(); }

    /** ความหนาแน่นของเสียง 0..100 */
    public int punch() { return sp.getInt("punch", 50); }
    public void setPunch(int v) { sp.edit().putInt("punch", v).apply(); }

    /** ตัดเบสที่ต่ำกว่าค่านี้ (Hz), 0 = ไม่ตัด */
    public int lowCut() { return sp.getInt("lowCut", 0); }
    public void setLowCut(int v) { sp.edit().putInt("lowCut", v).apply(); }

    /** ปรับเสียงแหลมในโหมด MAX, dB (-10..+2) */
    public int treble() { return sp.getInt("treble", -3); }
    public void setTreble(int v) { sp.edit().putInt("treble", v).apply(); }

    /** ความนุ่ม 0..100: เติมเสียงต่ำ-กลาง และลดช่วงที่บาดหู */
    public int soft() { return sp.getInt("soft", 40); }
    public void setSoft(int v) { sp.edit().putInt("soft", v).apply(); }

    public String preset() { return sp.getString("preset", "flat"); }
    public void setPreset(String v) { sp.edit().putString("preset", v).apply(); }
}
