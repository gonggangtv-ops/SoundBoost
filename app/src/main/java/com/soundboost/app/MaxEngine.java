package com.soundboost.app;

import android.media.audiofx.DynamicsProcessing;

/**
 * ตัวประมวลผลเสียงหลัก (Android 9 ขึ้นไป)
 *  โหมด MAX:
 *   1) ปรับโทน 8 ช่อง: ตัดเบสที่เล่นไม่ได้ / เบส (ปรับตามระดับเสียง) / ช่วงอู้ / กลาง /
 *      ช่วงบาดหู / ช่วงต่อ / ความกังวาล
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
                    true, 8,            // pre-EQ 8 ช่อง
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
     * เบสที่เพิ่ม/ลดตามระดับเสียง (dB) ใช้เฉพาะช่วงเบสจริง (ต่ำกว่า 150 Hz)
     * เปิดเบา (≤30%) +4 dB เพราะหูได้ยินเบสน้อยลง, เปิดดัง (≥90%) -3 dB กันลำโพงแตก
     */
    static float autoBassDb(float volume) {
        float t = Math.max(0f, Math.min(1f, (volume - 0.3f) / 0.6f));
        return 4f + (-3f - 4f) * t;
    }

    void apply(Prefs p, float volume) {
        try {
            boolean max = p.maxOn();
            double[] corr = p.calibOn() ? CalibMath.decode(p.calib()) : null;
            if (!max && corr == null) { dp.setEnabled(false); return; }

            float soft = p.soft() / 100f;
            float clarity = p.clarity() / 100f;
            float auto = p.autoBass() ? autoBassDb(volume) : 0f;
            float m = max ? 1f : 0f;

            // 1) ปรับโทน (ค่าความถี่ = ขอบบนของแต่ละช่อง ต้องเรียงจากต่ำไปสูง)
            int cut = max ? p.lowCut() : 0;
            float bassEdge = Math.max(150f, cut * 1.25f);
            float mudEdge = Math.max(400f, bassEdge * 2f);
            DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, max, 8);
            if (cut > 0) {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, cut * 0.8f, -24f));  // ใต้จุดตัด: ตัดทิ้ง
                pre.setBand(1, new DynamicsProcessing.EqBand(true, cut, -6f));          // ช่วงต่อ
            } else {
                pre.setBand(0, new DynamicsProcessing.EqBand(true, 20f, 0f));
                pre.setBand(1, new DynamicsProcessing.EqBand(true, 40f, 0f));
            }
            pre.setBand(2, new DynamicsProcessing.EqBand(true, bassEdge, m * (p.maxBass() + auto)));  // เบส
            pre.setBand(3, new DynamicsProcessing.EqBand(true, mudEdge, m * (2f - 6f * clarity)));    // ช่วงอู้
            pre.setBand(4, new DynamicsProcessing.EqBand(true, 2000f, 0f));                           // เสียงร้อง
            pre.setBand(5, new DynamicsProcessing.EqBand(true, 5000f, m * (-5f * soft)));             // ช่วงบาดหู
            pre.setBand(6, new DynamicsProcessing.EqBand(true, 9000f, m * (0.4f * p.treble())));      // ช่วงต่อ
            pre.setBand(7, new DynamicsProcessing.EqBand(true, 20000f, m * p.treble()));              // ความกังวาล
            dp.setPreEqAllChannelsTo(pre);

            // 2) คอมเพรสเซอร์ 4 ย่าน
            float k = p.punch() / 100f;
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, max, 4);
            mbc.setBand(0, band(150f,  k, 1.15f, 25f, 150f, 1f));
            mbc.setBand(1, band(1500f, k, 1.0f,  12f, 100f, 1f));
            mbc.setBand(2, band(5500f, k, 0.9f,  3f,  60f, 0.4f - 0.3f * soft));
            // แหลม: ปกติแค่กดยอดเสียง ส/ซ, ถ้าเปิด "ประกายเสียง" จะยกเสียงสูงที่เบาขึ้นมาด้วย (สไตล์ MaxxTreble)
            float sp = max ? p.sparkle() / 100f : 0f;
            mbc.setBand(3, new DynamicsProcessing.MbcBand(
                    true, 20000f, 2f, 50f, 3f + sp, -18f - 10f * sp, 6f, -90f, 1f, 0f, 6f * sp));
            dp.setMbcAllChannelsTo(mbc);

            // 4) EQ แก้เสียงลำโพงจากการวัด
            DynamicsProcessing.Eq post = new DynamicsProcessing.Eq(true, corr != null, POST_BANDS);
            post.setBand(0, new DynamicsProcessing.EqBand(true, (float) (CalibMath.CENTERS[0] / CalibMath.EDGE), 0f));
            for (int i = 0; i < CalibMath.CENTERS.length; i++) {
                float g = 0f;
                if (corr != null) {
                    g = (float) corr[i] * (p.calibAmount() / 75f);   // ผลวัดเก็บไว้ที่ 75% → ปรับตามแถบ
                    // ไมค์มือถือวัดเสียงสูงได้ไม่แม่น: ห้ามตัดเหนือ 4 kHz เกิน 3 dB
                    if (CalibMath.CENTERS[i] > 4000) g = Math.max(g, -3f);
                    g = Math.max(-9f, Math.min(3f, g));
                }
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
