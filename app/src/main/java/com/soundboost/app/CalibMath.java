package com.soundboost.app;

import java.util.Arrays;
import java.util.Random;

/**
 * คณิตศาสตร์ของการวัดลำโพงอัตโนมัติ (ไม่มีโค้ด Android ทดสอบบนคอมได้)
 * หลักการ: เล่น pink noise ออกลำโพง อัดกลับด้วยไมค์ แล้วเทียบพลังงานทีละ 1/3 ออกเทฟ
 */
final class CalibMath {
    static final int RATE = 48000;
    static final int FFT = 8192;

    /** ความถี่กลางแบบ 1/3 ออกเทฟ 125 Hz .. 12.5 kHz (21 ช่อง) */
    static final double[] CENTERS = new double[21];
    static {
        for (int i = 0; i < CENTERS.length; i++) CENTERS[i] = 1000.0 * Math.pow(2, (i - 9) / 3.0);
    }
    static final double EDGE = Math.pow(2, 1 / 6.0);

    static final double CORR_MIN_HZ = 200, CORR_MAX_HZ = 10000;  // ช่วงที่ไมค์มือถือเชื่อถือได้
    static final double MAX_CUT = -9, MAX_BOOST = 3;

    /** pink noise (ตัวกรองของ Paul Kellet) ค่อยๆ ดังขึ้น/เบาลงที่หัวท้าย */
    static short[] pinkNoise(int samples, long seed) {
        Random r = new Random(seed);
        short[] out = new short[samples];
        double b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
        int fade = RATE / 10;
        for (int i = 0; i < samples; i++) {
            double w = r.nextGaussian() * 0.1;
            b0 = 0.99886 * b0 + w * 0.0555179;
            b1 = 0.99332 * b1 + w * 0.0750759;
            b2 = 0.96900 * b2 + w * 0.1538520;
            b3 = 0.86650 * b3 + w * 0.3104856;
            b4 = 0.55000 * b4 + w * 0.5329522;
            b5 = -0.7616 * b5 - w * 0.0168980;
            double pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362;
            b6 = w * 0.115926;
            double g = Math.min(1.0, Math.min(i, samples - 1 - i) / (double) fade);
            double v = Math.max(-1, Math.min(1, pink * 0.35 * g));
            out[i] = (short) (v * 32767);
        }
        return out;
    }

    /** ระดับพลังงาน (dB) ต่อช่อง 1/3 ออกเทฟ เฉลี่ยหลายเฟรม (Welch) */
    static double[] bandLevels(short[] x, int from, int to) {
        double[] win = new double[FFT];
        for (int i = 0; i < FFT; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT - 1));
        double[] power = new double[FFT / 2];
        double[] re = new double[FFT], im = new double[FFT];
        int frames = 0;
        for (int start = from; start + FFT <= to; start += FFT / 2) {
            for (int i = 0; i < FFT; i++) { re[i] = x[start + i] / 32768.0 * win[i]; im[i] = 0; }
            fft(re, im);
            for (int k = 0; k < FFT / 2; k++) power[k] += re[k] * re[k] + im[k] * im[k];
            frames++;
        }
        double[] out = new double[CENTERS.length];
        double binHz = (double) RATE / FFT;
        for (int b = 0; b < CENTERS.length; b++) {
            int lo = (int) Math.ceil(CENTERS[b] / EDGE / binHz);
            int hi = (int) Math.floor(CENTERS[b] * EDGE / binHz);
            double sum = 1e-20;
            for (int k = lo; k <= hi && k < power.length; k++) sum += power[k];
            out[b] = 10 * Math.log10(sum / Math.max(1, frames));
        }
        return out;
    }

    /** ผลการวัด */
    static final class Result {
        boolean ok;
        String error;
        double[] correction;   // dB ต่อช่อง (ติดลบ = กดลง)
        double[] response;     // การตอบสนองของลำโพงที่วัดได้ (ปรับให้ช่วงกลาง = 0)
    }

    /**
     * @param ref       ระดับของสัญญาณทดสอบเอง
     * @param measured  ระดับที่ไมค์อัดได้ตอนเล่นเสียงทดสอบ
     * @param ambient   ระดับเสียงรบกวนในห้องก่อนเล่น
     * @param peak      ค่าสูงสุดที่อัดได้ (0..1) ใช้เช็กว่าไมค์อิ่มตัวไหม
     */
    static Result analyze(double[] ref, double[] measured, double[] ambient, double peak) {
        Result r = new Result();
        int n = CENTERS.length;

        if (peak > 0.97) {
            r.error = "เสียงดังเกินไมค์จะรับได้ ลดเสียงลง หรือวางมือถือให้ห่างลำโพงขึ้น แล้ววัดใหม่";
            return r;
        }

        // เช็กว่าเสียงทดสอบดังกว่าเสียงรบกวนพอไหม
        int checked = 0, good = 0;
        for (int i = 0; i < n; i++) {
            if (CENTERS[i] < 250 || CENTERS[i] > 8000) continue;
            checked++;
            if (measured[i] - ambient[i] >= 10) good++;
        }
        if (good < checked * 0.8) {
            r.error = "ไมค์ได้ยินเสียงทดสอบไม่ชัด เพิ่มเสียงลำโพง วางมือถือใกล้ขึ้น หรือหาที่เงียบกว่านี้ แล้ววัดใหม่";
            return r;
        }

        double[] resp = new double[n];
        for (int i = 0; i < n; i++) resp[i] = measured[i] - ref[i];

        // ใช้ช่วง 300 Hz - 3 kHz เป็นระดับอ้างอิง
        double[] mids = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) if (CENTERS[i] >= 300 && CENTERS[i] <= 3000) mids[m++] = resp[i];
        double[] sorted = Arrays.copyOf(mids, m);
        Arrays.sort(sorted);
        double norm = sorted[m / 2];
        for (int i = 0; i < n; i++) resp[i] -= norm;

        // เป้าหมาย: เกือบเรียบ ลาดลงนิดเดียวเหนือ 2 kHz (โทนเสียงให้ผู้ใช้ปรับเองด้วยแถบในโหมด MAX)
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) {
            double target = 0;   // เป้าหมายเรียบ ไม่ลดเสียงสูงเพิ่ม
            dev[i] = resp[i] - target;
        }
        // เกลี่ยให้เรียบ กันการแก้จุดเล็กๆ ที่เกิดจากห้อง
        double[] smooth = new double[n];
        for (int i = 0; i < n; i++) {
            double a = dev[Math.max(0, i - 1)], b = dev[i], c = dev[Math.min(n - 1, i + 1)];
            smooth[i] = 0.25 * a + 0.5 * b + 0.25 * c;
        }

        double[] corr = new double[n];
        for (int i = 0; i < n; i++) {
            double f = CENTERS[i];
            if (f < CORR_MIN_HZ || f > CORR_MAX_HZ) { corr[i] = 0; continue; }
            double c = -smooth[i] * 0.75;                  // แก้ 75% ไม่แก้เต็มที่
            corr[i] = Math.max(MAX_CUT, Math.min(MAX_BOOST, c));
        }
        r.ok = true;
        r.correction = corr;
        r.response = resp;
        return r;
    }

    static String encode(double[] c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Math.round(c[i] * 10) / 10.0);
        }
        return sb.toString();
    }

    static double[] decode(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        if (parts.length != CENTERS.length) return null;
        double[] c = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) c[i] = Double.parseDouble(parts[i]);
        } catch (NumberFormatException e) { return null; }
        return c;
    }

    static String hzLabel(double hz) {
        if (hz >= 1000) {
            double k = hz / 1000;
            return (k >= 10 ? String.valueOf(Math.round(k)) : String.format(java.util.Locale.US, "%.1f", k)) + " kHz";
        }
        return Math.round(hz) + " Hz";
    }

    /** FFT แบบ radix-2 ในที่ */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }
}
