package com.soundboost.app;

import android.media.audiofx.DynamicsProcessing;

/**
 * โหมด MAX (Android 9 ขึ้นไป)
 *  1) ตัดเบสความถี่ต่ำที่ลำโพงเล่นไม่ได้  -> ลำโพงไม่เสียแรงเปล่า ดังได้มากขึ้นโดยไม่แตก
 *  2) คอมเพรสเซอร์ 3 ย่าน (เบส / กลาง / แหลม) -> เสียงแน่นและดังสม่ำเสมอ
 *  3) แรงขับ + ลิมิตเตอร์ -> ดันความดังขึ้น แต่คุมไม่ให้สัญญาณเกินจนแตก
 */
final class MaxEngine {
    private final DynamicsProcessing dp;

    private static final float LIMIT_DB = -1.0f;

    private MaxEngine(DynamicsProcessing dp) { this.dp = dp; }

    static MaxEngine create(int priority, int session) {
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,          // สเตอริโอ
                    true, 4,    // pre-EQ 4 ช่อง (ใช้ตัดเบส)
                    true, 3,    // คอมเพรสเซอร์ 3 ย่าน
                    false, 0,   // ไม่ใช้ post-EQ
                    true)       // ลิมิตเตอร์
                    .setPreferredFrameDuration(10)
                    .build();
            return new MaxEngine(new DynamicsProcessing(priority, session, cfg));
        } catch (Throwable t) {
            return null;
        }
    }

    void apply(Prefs p) {
        try {
            boolean on = p.maxOn();
            if (!on) { dp.setEnabled(false); return; }

            // 1) ตัดเบส: ช่อง 0 = ใต้จุดตัด (ลดหนัก), ช่อง 1 = ช่วงต่อ (ลดเล็กน้อย)
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, 4);
            int cut = p.lowCut();
            if (cut > 0) {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, cut * 0.8f, -24f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, cut, -6f));
            } else {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, 20f, 0f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, 40f, 0f));
            }
            pre.setBand(2, new DynamicsProcessing.EqBand(true, 5000f, 0f));
            pre.setBand(3, new DynamicsProcessing.EqBand(true, 20000f, p.treble()));   // ความแหลม
            dp.setPreEqAllChannelsTo(pre);

            // 2) คอมเพรสเซอร์ 3 ย่าน
            float k = p.punch() / 100f;                 // 0..1
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, 3);
            mbc.setBand(0, band(180f,  k, 1.15f, 20f, 120f, 1f));    // เบส: คุมให้กระชับ
            mbc.setBand(1, band(4000f, k, 1.0f,  10f,  80f, 1f));    // กลาง: เสียงร้อง
            // แหลม: ไม่ชดเชยความดัง แค่กดยอดเสียงที่บาดหู (เช่น เสียง ส/ซ ฉาบ)
            mbc.setBand(2, new DynamicsProcessing.MbcBand(
                    true, 20000f, 2f, 50f, 3f, -16f, 6f, -90f, 1f, 0f, 0f));
            dp.setMbcAllChannelsTo(mbc);

            // 3) แรงขับ + ลิมิตเตอร์
            dp.setInputGainAllChannelsTo(p.drive());
            DynamicsProcessing.Limiter lim = new DynamicsProcessing.Limiter(
                    true, true, 0,
                    1f,      // attack ms
                    60f,     // release ms
                    10f,     // ratio
                    LIMIT_DB,
                    0f);
            dp.setLimiterAllChannelsTo(lim);

            dp.setEnabled(true);
        } catch (Throwable ignored) { }
    }

    /** ยิ่ง k มาก ยิ่งบีบมากและชดเชยความดังกลับมามาก */
    private static DynamicsProcessing.MbcBand band(float cutoff, float k, float weight, float attack, float release, float makeupScale) {
        float amt = Math.min(1f, k * weight);
        float threshold = -8f - 20f * amt;          // -8 .. -28 dB
        float ratio = 1.5f + 2.5f * amt;            // 1.5 .. 4
        float makeup = (-threshold) * (1f - 1f / ratio) * 0.45f * makeupScale;
        return new DynamicsProcessing.MbcBand(
                true, cutoff, attack, release, ratio, threshold,
                6f,      // knee
                -90f,    // ไม่ใช้ noise gate
                1f,      // expander ratio
                0f,      // pre gain
                makeup); // post gain
    }

    void release() {
        try { dp.release(); } catch (Throwable ignored) { }
    }
}
