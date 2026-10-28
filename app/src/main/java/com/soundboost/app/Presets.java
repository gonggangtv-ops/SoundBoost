package com.soundboost.app;

/** โหมดเสียงสำเร็จรูป: กำหนดเป็น dB ที่ความถี่หลัก แล้วแปลงให้เข้ากับช่อง EQ ของเครื่อง */
public final class Presets {
    public static final class Preset {
        public final String id, name;
        final double[] hz, db;
        public final int bass, loud;
        Preset(String id, String name, double[] db, int bass, int loud) {
            this.id = id; this.name = name; this.hz = POINTS; this.db = db; this.bass = bass; this.loud = loud;
        }
    }

    private static final double[] POINTS = {60, 230, 910, 3600, 14000};

    public static final Preset[] ALL = {
        new Preset("flat",   "ปกติ",          new double[]{ 0,  0,  0, 0, 0},   0,   0),
        new Preset("maxx",   "สไตล์ Maxx",    new double[]{ 3, -2,  0, 1, 3},   0,   0),
        new Preset("clear",  "ใส เบสชัด",     new double[]{ 2, -2,  0, 0, 2},   0,   0),
        new Preset("warm",   "นุ่มลึก",        new double[]{ 3,  2,  0,-2,-1}, 200,   0),
        new Preset("small",  "ลำโพงเล็ก",     new double[]{ 2,  6,  1, 3, 2}, 300, 300),
        new Preset("bass",   "เบสหนัก",       new double[]{ 8,  4,  0, 1, 2}, 600,   0),
        new Preset("vocal",  "เสียงร้องชัด",  new double[]{-2, -1,  3, 5, 2},   0, 200),
        new Preset("pop",    "ป๊อป/ร็อก",     new double[]{ 5,  2, -1, 3, 5}, 300,   0),
        new Preset("loud",   "กลางแจ้ง",      new double[]{ 4,  3,  2, 4, 3}, 400, 700),
    };

    public static Preset find(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }

    /** หาค่า dB ที่ความถี่ใดๆ โดยเฉลี่ยจุดใกล้เคียงบนสเกล log */
    public static int levelAt(Preset p, int freqHz) {
        double x = Math.log(Math.max(20, freqHz));
        double[] h = p.hz, d = p.db;
        if (x <= Math.log(h[0])) return (int) Math.round(d[0] * 100);
        for (int i = 0; i < h.length - 1; i++) {
            double a = Math.log(h[i]), b = Math.log(h[i + 1]);
            if (x <= b) {
                double t = (x - a) / (b - a);
                return (int) Math.round((d[i] + t * (d[i + 1] - d[i])) * 100);
            }
        }
        return (int) Math.round(d[d.length - 1] * 100);
    }
}
