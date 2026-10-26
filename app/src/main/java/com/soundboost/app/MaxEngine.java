package com.soundboost.app;

import android.media.audiofx.DynamicsProcessing;

/**
 * โหมด MAX (Android 9 ขึ้นไป)
 *  1) ปรับโทน: ตัดเบสที่ลำโพงเล่นไม่ได้, เติมความอุ่นช่วงเสียงต่ำ-กลาง, ลดช่วงเสียงที่บาดหู
 *  2) คอมเพรสเซอร์ 4 ย่าน (เบส / อุ่น / ช่วงบาดหู / แหลม) -> ดังสม่ำเสมอแต่ไม่แหลมจัด
 *  3) แรงขับ + ลิมิตเตอร์ -> ดันความดังขึ้น แต่คุมไม่ให้สัญญาณเกินจนแตก
 */
final class MaxEngine {
    private final DynamicsProcessing dp;

    /** เว้นเพดานไว้นิดหน่อย กันเสียงแตกตอนส่งผ่านบลูทูธ */
    private static final float LIMIT_DB = -1.5f;

    private MaxEngine(DynamicsProcessing dp) { this.dp = dp; }

    static MaxEngine create(int priority, int session) {
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,          // สเตอริโอ
                    true, 6,    // pre-EQ 6 ช่อง
                    true, 4,    // คอมเพรสเซอร์ 4 ย่าน
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
            if (!p.maxOn()) { dp.setEnabled(false); return; }

            float soft = p.soft() / 100f;               // ความนุ่ม 0..1

            // 1) ปรับโทน (ค่าความถี่ = ขอบบนของแต่ละช่อง)
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, 6);
            int cut = p.lowCut();
            if (cut > 0) {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, cut * 0.8f, -24f));  // ใต้จุดตัด: ตัดทิ้ง
                pre.setBand(1, new DynamicsProcessing.EqBand(true, cut, -6f));          // ช่วงต่อ
            } else {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, 20f, 0f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, 40f, 0f));
            }
            pre.setBand(2, new DynamicsProcessing.EqBand(true, 350f, 4f * soft));     // ความอุ่น/ลึก
            pre.setBand(3, new DynamicsProcessing.EqBand(true, 2000f, 0f));           // เสียงร้อง
            pre.setBand(4, new DynamicsProcessing.EqBand(true, 6000f, -5f * soft));   // ช่วงบาดหู
            pre.setBand(5, new DynamicsProcessing.EqBand(true, 20000f, p.treble()));  // ความแหลม
            dp.setPreEqAllChannelsTo(pre);

            // 2) คอมเพรสเซอร์ 4 ย่าน
            float k = p.punch() / 100f;                 // 0..1
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, 4);
            mbc.setBand(0, band(150f,  k, 1.15f, 25f, 150f, 1f));             // เบส: กระชับ ไม่ให้ลิมิตเตอร์ทำงานหนัก
            mbc.setBand(1, band(1500f, k, 1.0f,  12f, 100f, 1f));             // ต่ำ-กลาง: ความหนาและเสียงร้อง
            mbc.setBand(2, band(5500f, k, 0.9f,  3f,  60f, 0.4f - 0.3f * soft)); // ช่วงบาดหู: กดยอด ชดเชยน้อย
            // แหลม: ไม่ชดเชยความดัง แค่กดยอดเสียง ส/ซ และฉาบ
            mbc.setBand(3, new DynamicsProcessing.MbcBand(
                    true, 20000f, 2f, 50f, 3f, -18f, 6f, -90f, 1f, 0f, 0f));
            dp.setMbcAllChannelsTo(mbc);

            // 3) แรงขับ + ลิมิตเตอร์ (ปล่อยช้าลง เสียงเบสจะไม่บี้)
            dp.setInputGainAllChannelsTo(p.drive());
            DynamicsProcessing.Limiter lim = new DynamicsProcessing.Limiter(
                    true, true, 0,
                    2f,      // attack ms
                    150f,    // release ms
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
        float makeup = (-threshold) * (1f - 1f / ratio) * 0.45f * Math.max(0f, makeupScale);
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
