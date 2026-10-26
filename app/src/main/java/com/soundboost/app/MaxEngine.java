package com.soundboost.app;

import android.media.audiofx.DynamicsProcessing;

/**
 * ตัวประมวลผลเสียงหลัก (Android 9 ขึ้นไป)
 *  โหมด MAX:
 *   1) ปรับโทน: ตัดเบสที่ลำโพงเล่นไม่ได้, เติมความอุ่น (ปรับตามระดับเสียง), ลดช่วงที่บาดหู
 *   2) คอมเพรสเซอร์ 4 ย่าน (เบส / อุ่น / ช่วงบาดหู / แหลม)
 *   3) แรงขับ
 *  ปรับอัตโนมัติจากไมค์:
 *   4) EQ แก้เสียงลำโพง 21 ช่อง (ใช้ได้แม้ปิดโหมด MAX)
 *  ทุกโหมด:
 *   5) ลิมิตเตอร์กันแตก
 */
final class MaxEngine {
    private final DynamicsProcessing dp;

    private static final float LIMIT_DB = -1.5f;
    /** ช่องแรก (ต่ำกว่า 125 Hz) + 21 ช่องวัด + ช่องท้าย (สูงกว่า 12.5 kHz) */
    static final int POST_BANDS = CalibMath.CENTERS.length + 2;

    private MaxEngine(DynamicsProcessing dp) { this.dp = dp; }

    static MaxEngine create(int priority, int session) {
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,                  // สเตอริโอ
                    true, 6,            // pre-EQ 6 ช่อง
                    true, 4,            // คอมเพรสเซอร์ 4 ย่าน
                    true, POST_BANDS,   // EQ แก้เสียงจากการวัด
                    true)               // ลิมิตเตอร์
                    .setPreferredFrameDuration(10)
                    .build();
            return new MaxEngine(new DynamicsProcessing(priority, session, cfg));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * ความอุ่นที่เพิ่ม/ลดตามระดับเสียง (dB)
     * เปิดเบา (≤30%) +5 dB เพราะหูได้ยินเบสน้อยลง, เปิดดัง (≥90%) -2 dB กันลำโพงแตก
     */
    static float autoBassDb(float volume) {
        float t = Math.max(0f, Math.min(1f, (volume - 0.3f) / 0.6f));
        return 5f + (-2f - 5f) * t;
    }

    void apply(Prefs p, float volume) {
        try {
            boolean max = p.maxOn();
            double[] corr = p.calibOn() ? CalibMath.decode(p.calib()) : null;
            if (!max && corr == null) { dp.setEnabled(false); return; }

            float soft = p.soft() / 100f;
            float auto = p.autoBass() ? autoBassDb(volume) : 0f;

            // 1) ปรับโทน (ค่าความถี่ = ขอบบนของแต่ละช่อง)
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, max, 6);
            int cut = p.lowCut();
            if (max && cut > 0) {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, cut * 0.8f, -24f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, cut, -6f));
            } else {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, 20f, 0f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, 40f, 0f));
            }
            pre.setBand(2, new DynamicsProcessing.EqBand(true, 350f, max ? 4f * soft + auto : 0f));
            pre.setBand(3, new DynamicsProcessing.EqBand(true, 2000f, 0f));
            pre.setBand(4, new DynamicsProcessing.EqBand(true, 6000f, max ? -5f * soft : 0f));
            pre.setBand(5, new DynamicsProcessing.EqBand(true, 20000f, max ? p.treble() : 0f));
            dp.setPreEqAllChannelsTo(pre);

            // 2) คอมเพรสเซอร์ 4 ย่าน
            float k = p.punch() / 100f;
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, max, 4);
            mbc.setBand(0, band(150f,  k, 1.15f, 25f, 150f, 1f));
            mbc.setBand(1, band(1500f, k, 1.0f,  12f, 100f, 1f));
            mbc.setBand(2, band(5500f, k, 0.9f,  3f,  60f, 0.4f - 0.3f * soft));
            mbc.setBand(3, new DynamicsProcessing.MbcBand(
                    true, 20000f, 2f, 50f, 3f, -18f, 6f, -90f, 1f, 0f, 0f));
            dp.setMbcAllChannelsTo(mbc);

            // 4) EQ แก้เสียงลำโพงจากการวัด
            DynamicsProcessing.Eq post = new DynamicsProcessing.Eq(true, corr != null, POST_BANDS);
            post.setBand(0, new DynamicsProcessing.EqBand(true, (float) (CalibMath.CENTERS[0] / CalibMath.EDGE), 0f));
            for (int i = 0; i < CalibMath.CENTERS.length; i++) {
                float g = corr != null ? (float) corr[i] : 0f;
                post.setBand(i + 1, new DynamicsProcessing.EqBand(true, (float) (CalibMath.CENTERS[i] * CalibMath.EDGE), g));
            }
            post.setBand(POST_BANDS - 1, new DynamicsProcessing.EqBand(true, 20000f, 0f));
            dp.setPostEqAllChannelsTo(post);

            // 3) แรงขับ + 5) ลิมิตเตอร์
            dp.setInputGainAllChannelsTo(max ? p.drive() : 0f);
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                    true, true, 0, 2f, 150f, 10f, LIMIT_DB, 0f));

            dp.setEnabled(true);
        } catch (Throwable ignored) { }
    }

    void disable() {
        try { dp.setEnabled(false); } catch (Throwable ignored) { }
    }

    private static DynamicsProcessing.MbcBand band(float cutoff, float k, float weight, float attack, float release, float makeupScale) {
        float amt = Math.min(1f, k * weight);
        float threshold = -8f - 20f * amt;
        float ratio = 1.5f + 2.5f * amt;
        float makeup = (-threshold) * (1f - 1f / ratio) * 0.45f * Math.max(0f, makeupScale);
        return new DynamicsProcessing.MbcBand(
                true, cutoff, attack, release, ratio, threshold, 6f, -90f, 1f, 0f, makeup);
    }

    void release() {
        try { dp.release(); } catch (Throwable ignored) { }
    }
}
