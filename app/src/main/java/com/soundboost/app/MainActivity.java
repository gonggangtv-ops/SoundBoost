package com.soundboost.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    // สี
    private static final int BG = Color.parseColor("#0E1014");
    private static final int CARD = Color.parseColor("#181B22");
    private static final int LINE = Color.parseColor("#262A33");
    private static final int TEXT = Color.parseColor("#F2F4F7");
    private static final int MUTED = Color.parseColor("#8C95A5");
    private static final int ACCENT = Color.parseColor("#3DDBB4");
    private static final int WARN = Color.parseColor("#F5B94A");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private EqInfo info;

    private Switch power;
    private TextView status;
    private final List<TextView> chips = new ArrayList<>();
    private final List<SeekBar> bandBars = new ArrayList<>();
    private SeekBar bassBar, loudBar;

    // โหมด MAX
    private Switch maxSwitch;
    private SeekBar driveBar, punchBar, maxBassBar, clarityBar, softBar, trebleBar, testBar;
    private TextView lowCutValue, playBtn;
    private LinearLayout testPanel;
    private final ToneGen tone = new ToneGen();
    private int savedLowCut = 0;

    // ปรับอัตโนมัติ
    private static final int REQ_MIC = 2;
    private Switch calibSwitch, autoBassSwitch;
    private TextView calibBtn, calibStatus;
    private CurveView curve;
    private boolean calibrating = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        info = EqInfo.get(this);
        setContentView(buildUi());
        loadValues();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // ถ้าระบบปิดแอปไปแต่ผู้ใช้ยังเปิดสวิตช์ไว้ ให้เริ่มใหม่
        if (prefs.enabled() && AudioService.instance == null) startEngine();
        power.setChecked(prefs.enabled());
        refreshStatusSoon();
    }

    @Override protected void onPause() {
        super.onPause();
        if (testPanel != null && testPanel.getVisibility() == View.VISIBLE) closeTest(false);
    }

    // ================= UI =================

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(root);

        TextView title = text("SoundBoost", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = text("ปรับเสียงลำโพงบลูทูธให้ดีขึ้น", 15, MUTED);
        root.addView(sub, lp(0, dp(2), 0, dp(18)));

        // --- สวิตช์หลัก ---
        LinearLayout powerCard = card(root);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView pl = text("เปิดปรับเสียง", 19, TEXT);
        pl.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(pl, new LinearLayout.LayoutParams(0, -2, 1));
        power = new Switch(this);
        power.setScaleX(1.25f); power.setScaleY(1.25f);
        row.addView(power);
        powerCard.addView(row);
        status = text("", 14, MUTED);
        powerCard.addView(status, lp(0, dp(6), 0, 0));
        power.setOnCheckedChangeListener((v, on) -> {
            if (on == prefs.enabled()) return;
            prefs.setEnabled(on);
            if (on) startEngine(); else stopService(new Intent(this, AudioService.class));
            refreshStatusSoon();
        });

        // --- โหมดสำเร็จรูป ---
        root.addView(sectionTitle("โหมดเสียง"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        hs.addView(chipRow);
        for (Presets.Preset p : Presets.ALL) {
            TextView c = text(p.name, 15, TEXT);
            c.setPadding(dp(16), dp(10), dp(16), dp(10));
            c.setTag(p.id);
            c.setOnClickListener(v -> applyPreset(p));
            chips.add(c);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.setMargins(0, 0, dp(8), 0);
            chipRow.addView(c, cp);
        }
        root.addView(hs, lp(0, 0, 0, dp(4)));

        // --- อีควอไลเซอร์ ---
        root.addView(sectionTitle("อีควอไลเซอร์"));
        LinearLayout eqCard = card(root);
        int steps = (info.maxLevel - info.minLevel) / 100;
        for (int i = 0; i < info.bands; i++) {
            final int band = i;
            SeekBar bar = sliderRow(eqCard, EqInfo.label(info.freqHz[i]), steps, (value, fromUser) -> {
                int mB = info.minLevel + value * 100;
                if (fromUser) { prefs.setBand(band, mB); markCustom(); push(); }
                return signedDb(mB);
            });
            bandBars.add(bar);
        }
        TextView eqHint = text("ซ้าย = เบส (เสียงต่ำ)   ขวา = แหลม (เสียงสูง)", 12, MUTED);
        eqCard.addView(eqHint, lp(0, dp(4), 0, 0));

        // --- เบสและความดัง ---
        root.addView(sectionTitle("เสริมพลัง"));
        LinearLayout boostCard = card(root);
        bassBar = sliderRow(boostCard, "เพิ่มเบส", 100, (value, fromUser) -> {
            if (fromUser) { prefs.setBass(value * 10); markCustom(); push(); }
            return value + "%";
        });
        loudBar = sliderRow(boostCard, "เพิ่มความดัง", 15, (value, fromUser) -> {
            if (fromUser) { prefs.setLoud(value * 100); markCustom(); push(); }
            return "+" + value + " dB";
        });
        TextView warn = text("ถ้าเสียงแตกหรือพร่า ให้ลด \"เพิ่มเบส\" หรือ \"เพิ่มความดัง\" ลงทีละนิด", 13, WARN);
        boostCard.addView(warn, lp(0, dp(6), 0, 0));

        // --- ปรับอัตโนมัติ ---
        root.addView(sectionTitle("ปรับอัตโนมัติ"));
        buildAutoCard(card(root));

        // --- โหมด MAX ---
        root.addView(sectionTitle("โหมด MAX"));
        buildMaxCard(card(root));

        // --- รีเซ็ต ---
        TextView reset = text("รีเซ็ตเป็นปกติ", 15, ACCENT);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(14), 0, dp(14));
        reset.setBackground(round(Color.TRANSPARENT, ACCENT, 12));
        reset.setOnClickListener(v -> applyPreset(Presets.find("flat")));
        root.addView(reset, lp(0, dp(18), 0, 0));

        // --- เคล็ดลับ ---
        root.addView(sectionTitle("เคล็ดลับ"));
        LinearLayout tips = card(root);
        tips.addView(text(
                "• ถ้าเปิดแล้วเสียงยังไม่เปลี่ยน ให้หยุดเพลงแล้วกดเล่นใหม่อีกครั้ง\n\n" +
                "• ลำโพงตัวเล็กเล่นเบสลึกมากไม่ได้ ดันเบสสุดจะทำให้เสียงแตก ลองโหมด \"ลำโพงเล็ก\" ก่อน\n\n" +
                "• เปิดเสียงที่ตัวลำโพงให้สูงไว้ แล้วคุมความดังที่มือถือ จะได้เสียงสะอาดกว่า\n\n" +
                "• ถ้าแอปหยุดทำงานเอง ให้ปิดโหมดประหยัดแบตเตอรี่สำหรับ SoundBoost ในการตั้งค่ามือถือ",
                14, MUTED));

        return scroll;
    }

    private void buildAutoCard(LinearLayout c) {
        TextView t = text("ให้แอปฟังลำโพงแล้วแก้เสียงให้เอง", 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        c.addView(text("หาจุดที่ลำโพงดังล้นจนบาดหู แล้วกดลงให้พอดี", 13, MUTED));

        c.addView(text(
                "1) หยุดเพลงก่อน และอยู่ในที่เงียบ\n" +
                "2) วางมือถือห่างลำโพงประมาณ 1 เมตร หันด้านล่างของมือถือ (ไมค์) เข้าหาลำโพง\n" +
                "3) ตั้งเสียงลำโพงระดับที่ฟังประจำ แล้วกดเริ่มวัด", 13, MUTED), lp(0, dp(10), 0, 0));

        calibBtn = button("เริ่มวัดด้วยไมค์", true);
        calibBtn.setOnClickListener(v -> startCalibration());
        c.addView(calibBtn, lp(0, dp(12), 0, 0));

        calibStatus = text("", 13, MUTED);
        c.addView(calibStatus, lp(0, dp(10), 0, 0));

        curve = new CurveView(this);
        c.addView(curve, new LinearLayout.LayoutParams(-1, dp(110)));

        calibSwitch = switchRow(c, "ใช้ผลที่วัดได้", "ปิดเพื่อฟังเทียบกับเสียงเดิม");
        calibSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on == prefs.calibOn()) return;
            prefs.setCalibOn(on);
            push();
            refreshStatusSoon();
        });

        LinearLayout line = new LinearLayout(this);
        line.setBackgroundColor(LINE);
        c.addView(line, lp(0, dp(14), 0, dp(4)));
        line.getLayoutParams().height = dp(1);

        autoBassSwitch = switchRow(c, "เบสตามระดับเสียง", "เปิดเบา = เบสลึกขึ้น · เปิดดัง = ลดเบสลงกันแตก");
        autoBassSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on == prefs.autoBass()) return;
            prefs.setAutoBass(on);
            push();
        });
    }

    private Switch switchRow(LinearLayout parent, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        LinearLayout labels = column();
        labels.addView(text(title, 15, TEXT));
        labels.addView(text(sub, 12, MUTED));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        row.addView(sw);
        parent.addView(row);
        return sw;
    }

    private void startCalibration() {
        if (calibrating) return;
        if (Build.VERSION.SDK_INT < 28) {
            showCalibMessage("การแก้เสียงอัตโนมัติต้องใช้ Android 9 ขึ้นไป", true);
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (tone.isPlaying() || (testPanel != null && testPanel.getVisibility() == View.VISIBLE)) closeTest(false);

        calibrating = true;
        calibBtn.setText("กำลังวัด…");
        calibBtn.setAlpha(0.6f);
        showCalibMessage("เตรียมตัว…", false);

        // ปิดเอฟเฟกต์ชั่วคราว จะได้วัดเสียงจริงของลำโพง
        AudioService svc = AudioService.instance;
        if (svc != null) svc.setBypass(true);

        new Thread(() -> {
            CalibMath.Result r = Calibrator.run(this, msg -> runOnUiThread(() -> showCalibMessage(msg, false)));
            runOnUiThread(() -> finishCalibration(r));
        }, "calibrate").start();
    }

    private void finishCalibration(CalibMath.Result r) {
        calibrating = false;
        calibBtn.setText("วัดใหม่");
        calibBtn.setAlpha(1f);
        AudioService svc = AudioService.instance;
        if (svc != null) svc.setBypass(false);

        if (!r.ok) {
            showCalibMessage(r.error, true);
            return;
        }
        prefs.setCalib(CalibMath.encode(r.correction));
        prefs.setCalibOn(true);
        calibSwitch.setEnabled(true);
        calibSwitch.setChecked(true);
        curve.setValues(r.correction);
        showCalibMessage(summary(r.correction), false);
        if (!prefs.enabled()) power.setChecked(true);
        push();
        refreshStatusSoon();
    }

    /** สรุปผลเป็นภาษาคน: จุดที่กดลงมากที่สุด 3 จุด และจุดที่เติม */
    private String summary(double[] c) {
        double[] f = CalibMath.CENTERS;
        List<Integer> cuts = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            boolean localMin = (i == 0 || c[i] <= c[i - 1]) && (i == c.length - 1 || c[i] <= c[i + 1]);
            if (localMin && c[i] <= -2) cuts.add(i);
        }
        cuts.sort((a, b) -> Double.compare(c[a], c[b]));
        StringBuilder sb = new StringBuilder();
        if (cuts.isEmpty()) {
            sb.append("ลำโพงตัวนี้เสียงค่อนข้างสมดุลอยู่แล้ว ปรับแค่เล็กน้อย");
        } else {
            sb.append("กดจุดที่บาดหูลง: ");
            for (int k = 0; k < Math.min(3, cuts.size()); k++) {
                int i = cuts.get(k);
                if (k > 0) sb.append(", ");
                sb.append(CalibMath.hzLabel(f[i])).append(" ").append(Math.round(c[i])).append(" dB");
            }
        }
        int best = -1;
        for (int i = 0; i < c.length; i++) if (c[i] >= 1.5 && (best < 0 || c[i] > c[best])) best = i;
        if (best >= 0) sb.append("\nเติมช่วงที่ขาด: ").append(CalibMath.hzLabel(f[best])).append(" +").append(Math.round(c[best])).append(" dB");
        sb.append("\nเปิดเพลงฟังได้เลย ลองปิดสวิตช์ \"ใช้ผลที่วัดได้\" เพื่อฟังเทียบ");
        return sb.toString();
    }

    private void showCalibMessage(String msg, boolean warn) {
        calibStatus.setText(msg);
        calibStatus.setTextColor(warn ? WARN : ACCENT);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCalibration();
        else showCalibMessage("ต้องอนุญาตให้ใช้ไมค์ก่อน แอปจะฟังเฉพาะตอนวัด ไม่มีการอัดเก็บไว้", true);
    }

    private void buildMaxCard(LinearLayout c) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        TextView t = text("ดังขึ้น แน่นขึ้น ไม่แตก", 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        labels.addView(t);
        labels.addView(text("บีบเสียงแยกเบส/กลาง/แหลม แล้วคุมด้วยลิมิตเตอร์", 13, MUTED));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        maxSwitch = new Switch(this);
        row.addView(maxSwitch);
        c.addView(row);

        if (Build.VERSION.SDK_INT < 28) {
            maxSwitch.setEnabled(false);
            c.addView(text("ต้องใช้ Android 9 ขึ้นไป", 13, WARN), lp(0, dp(8), 0, 0));
            return;
        }
        maxSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on == prefs.maxOn()) return;
            prefs.setMaxOn(on);
            push();
            refreshStatusSoon();
        });

        c.addView(text("กดเพื่อตั้งค่าทั้งหมดในครั้งเดียว", 13, MUTED), lp(0, dp(12), 0, dp(6)));
        LinearLayout quick = new LinearLayout(this);
        TextView clearBtn = button("ใสกังวาล เบสชัด", true);
        clearBtn.setOnClickListener(v -> applySound("clear"));
        TextView warmBtn = button("นุ่มลึก", false);
        warmBtn.setOnClickListener(v -> applySound("warm"));
        quick.addView(clearBtn, btnLp());
        quick.addView(warmBtn, btnLp());
        c.addView(quick, lp(0, 0, 0, dp(8)));

        driveBar = sliderRow(c, "แรงขับ", 12, (value, fromUser) -> {
            if (fromUser) { prefs.setDrive(value); push(); }
            return "+" + value + " dB";
        });
        punchBar = sliderRow(c, "ความแน่น", 100, (value, fromUser) -> {
            if (fromUser) { prefs.setPunch(value); push(); }
            return value + "%";
        });
        maxBassBar = sliderRow(c, "เบส", 8, (value, fromUser) -> {
            if (fromUser) { prefs.setMaxBass(value); push(); }
            return "+" + value + " dB";
        });
        clarityBar = sliderRow(c, "ความใส", 100, (value, fromUser) -> {
            if (fromUser) { prefs.setClarity(value); push(); }
            return value + "%";
        });
        softBar = sliderRow(c, "กันบาดหู", 100, (value, fromUser) -> {
            if (fromUser) { prefs.setSoft(value); push(); }
            return value + "%";
        });
        trebleBar = sliderRow(c, "ความกังวาล", 16, (value, fromUser) -> {
            int db = value - 10;                     // -10 .. +6 dB
            if (fromUser) { prefs.setTreble(db); push(); }
            return (db > 0 ? "+" : "") + db + " dB";
        });

        // ตัดเบสที่ลำโพงเล่นไม่ได้
        LinearLayout cutRow = new LinearLayout(this);
        cutRow.setGravity(Gravity.CENTER_VERTICAL);
        cutRow.setPadding(0, dp(8), 0, 0);
        LinearLayout cl = column();
        cl.addView(text("ตัดเบสที่ลำโพงเล่นไม่ได้", 14, TEXT));
        lowCutValue = text("", 13, ACCENT);
        cl.addView(lowCutValue);
        cutRow.addView(cl, new LinearLayout.LayoutParams(0, -2, 1));
        TextView measure = button("วัดลำโพง", false);
        measure.setOnClickListener(v -> openTest());
        cutRow.addView(measure);
        c.addView(cutRow);

        // แผงทดสอบ (ซ่อนไว้ก่อน)
        testPanel = column();
        testPanel.setVisibility(View.GONE);
        testPanel.setPadding(0, dp(12), 0, 0);
        testPanel.addView(text(
                "1) เปิดเสียงลำโพงระดับปกติ แล้วกด ▶ เล่น\n" +
                "2) เลื่อนแถบไปทางซ้ายช้าๆ ความถี่จะต่ำลง\n" +
                "3) หยุดตรงที่เสียงหึ่งเบาลงมาก หรือได้ยินเสียงลำโพงสั่นแปลกๆ\n" +
                "4) กด ใช้ค่านี้", 13, MUTED));
        testBar = sliderRow(testPanel, "ความถี่", 220, (value, fromUser) -> {
            tone.setFreq(30 + value);
            return (30 + value) + " Hz";
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(6), 0, 0);
        playBtn = button("▶ เล่น", false);
        playBtn.setOnClickListener(v -> {
            if (tone.isPlaying()) { tone.stop(); playBtn.setText("▶ เล่น"); }
            else { tone.setFreq(30 + testBar.getProgress()); tone.start(); playBtn.setText("■ หยุด"); }
        });
        TextView use = button("ใช้ค่านี้", true);
        use.setOnClickListener(v -> {
            savedLowCut = 30 + testBar.getProgress();
            closeTest(true);
        });
        TextView none = button("ไม่ตัด", false);
        none.setOnClickListener(v -> { savedLowCut = 0; closeTest(true); });
        btns.addView(playBtn, btnLp());
        btns.addView(use, btnLp());
        btns.addView(none, btnLp());
        testPanel.addView(btns);
        c.addView(testPanel);

        c.addView(text("เสียงอู้ = เพิ่มความใส · เบสไม่พอ = เพิ่มเบส · แสบหู = เพิ่มกันบาดหู · ทึบไม่มีประกาย = เพิ่มความกังวาล\nถ้าเสียงแตก ให้ลดแรงขับลงก่อน และถ้าใช้โหมด MAX อยู่ แถบ \"เพิ่มความดัง\" ด้านบนจะไม่ทำงาน", 13, MUTED), lp(0, dp(10), 0, 0));
    }

    /** ตั้งค่าเสียงสำเร็จรูปในครั้งเดียว (ไม่แตะค่าตัดเบสและผลวัดไมค์) */
    private void applySound(String id) {
        prefs.setMaxOn(true);
        prefs.setDrive(6);
        if ("clear".equals(id)) {        // ใสกังวาล เบสชัด
            prefs.setPunch(50);
            prefs.setMaxBass(4);
            prefs.setClarity(70);         // ลดช่วงอู้ประมาณ 2 dB
            prefs.setSoft(35);            // กันบาดหูเล็กน้อย
            prefs.setTreble(2);           // เติมประกายเสียงสูง
        } else {                          // นุ่มลึก
            prefs.setPunch(55);
            prefs.setMaxBass(4);
            prefs.setClarity(30);
            prefs.setSoft(70);
            prefs.setTreble(-2);
        }
        applyPreset(Presets.find(id));   // ตั้ง EQ แล้วโหลดค่าทั้งหมดขึ้นจอ + ส่งไปตัวปรับเสียง
        if (!prefs.enabled()) power.setChecked(true);   // เปิดปรับเสียงให้ด้วยถ้ายังปิดอยู่
        refreshStatusSoon();
        if (prefs.lowCut() == 0) {
            android.widget.Toast.makeText(this, "ตั้งค่าแล้ว แนะนำให้กด \"วัดลำโพง\" ด้วย เบสจะลึกขึ้นโดยไม่แตก", android.widget.Toast.LENGTH_LONG).show();
        } else {
            android.widget.Toast.makeText(this, "ตั้งค่าแล้ว", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void openTest() {
        savedLowCut = prefs.lowCut();
        prefs.setLowCut(0);          // ปิดการตัดเบสชั่วคราวระหว่างวัด
        push();
        testBar.setProgress(savedLowCut > 0 ? savedLowCut - 30 : 70);
        testPanel.setVisibility(View.VISIBLE);
    }

    private void closeTest(boolean save) {
        tone.stop();
        playBtn.setText("▶ เล่น");
        testPanel.setVisibility(View.GONE);
        prefs.setLowCut(savedLowCut);
        if (save && savedLowCut > 0 && !prefs.maxOn()) {
            prefs.setMaxOn(true);    // การตัดเบสทำงานในโหมด MAX
            maxSwitch.setChecked(true);
        }
        updateLowCutLabel();
        push();
    }

    private void updateLowCutLabel() {
        if (lowCutValue == null) return;
        int c = prefs.lowCut();
        lowCutValue.setText(c > 0 ? "ตัดต่ำกว่า " + c + " Hz" : "ยังไม่ได้ตั้ง (กดวัดลำโพง)");
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 14, primary ? BG : ACCENT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(round(primary ? ACCENT : Color.TRANSPARENT, ACCENT, 10));
        return b;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(0, 0, dp(8), 0);
        return p;
    }

    private interface SliderFn { String onValue(int value, boolean fromUser); }

    private SeekBar sliderRow(LinearLayout parent, String label, int max, SliderFn fn) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView l = text(label, 14, TEXT);
        row.addView(l, new LinearLayout.LayoutParams(dp(96), -2));

        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1));

        TextView val = text("", 14, ACCENT);
        val.setGravity(Gravity.END);
        val.setTypeface(Typeface.MONOSPACE);
        row.addView(val, new LinearLayout.LayoutParams(dp(68), -2));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { val.setText(fn.onValue(p, user)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        val.setText(fn.onValue(0, false));
        parent.addView(row);
        return bar;
    }

    // ================= การทำงาน =================

    private void loadValues() {
        for (int i = 0; i < bandBars.size(); i++) {
            bandBars.get(i).setProgress((info.clamp(prefs.band(i)) - info.minLevel) / 100);
        }
        bassBar.setProgress(prefs.bass() / 10);
        loudBar.setProgress(prefs.loud() / 100);
        highlightChip(prefs.preset());
        if (driveBar != null) {
            maxSwitch.setChecked(prefs.maxOn());
            driveBar.setProgress(prefs.drive());
            punchBar.setProgress(prefs.punch());
            maxBassBar.setProgress(prefs.maxBass());
            clarityBar.setProgress(prefs.clarity());
            softBar.setProgress(prefs.soft());
            trebleBar.setProgress(prefs.treble() + 10);
        }
        updateLowCutLabel();

        double[] corr = CalibMath.decode(prefs.calib());
        calibSwitch.setEnabled(corr != null);
        calibSwitch.setChecked(corr != null && prefs.calibOn());
        autoBassSwitch.setChecked(prefs.autoBass());
        if (corr != null) {
            curve.setValues(corr);
            calibBtn.setText("วัดใหม่");
            if (calibStatus.getText().length() == 0) showCalibMessage(summary(corr), false);
        }
    }

    private void applyPreset(Presets.Preset p) {
        for (int i = 0; i < info.bands; i++) {
            int mB = info.clamp(Presets.levelAt(p, info.freqHz[i]));
            mB = Math.round(mB / 100f) * 100;
            prefs.setBand(i, mB);
        }
        prefs.setBass(p.bass);
        prefs.setLoud(p.loud);
        prefs.setPreset(p.id);
        loadValues();
        push();
    }

    private void markCustom() {
        if (!"custom".equals(prefs.preset())) { prefs.setPreset("custom"); highlightChip("custom"); }
    }

    private void highlightChip(String id) {
        for (TextView c : chips) {
            boolean on = id.equals(c.getTag());
            c.setTextColor(on ? BG : TEXT);
            c.setBackground(round(on ? ACCENT : CARD, on ? ACCENT : LINE, 50));
        }
    }

    /** ส่งค่าใหม่ไปให้ตัวปรับเสียงที่ทำงานอยู่ */
    private void push() {
        AudioService s = AudioService.instance;
        if (s != null) s.applyAll();
    }

    private void startEngine() {
        startForegroundService(new Intent(this, AudioService.class));
    }

    private void refreshStatusSoon() {
        updateStatus();
        ui.postDelayed(this::updateStatus, 700);
        ui.postDelayed(this::updateStatus, 2500);
    }

    private void updateStatus() {
        AudioService s = AudioService.instance;
        if (!prefs.enabled()) {
            status.setText("ปิดอยู่ เสียงเป็นแบบเดิมของเครื่อง");
            status.setTextColor(MUTED);
        } else if (s == null) {
            status.setText("กำลังเริ่มทำงาน…");
            status.setTextColor(MUTED);
        } else {
            int apps = s.appSessionCount();
            String t;
            if (s.isGlobalOk()) {
                t = "● ทำงานกับเสียงทั้งเครื่อง";
                if (apps > 0) t += " และแอปเพลง " + apps + " แอป";
            } else if (apps > 0) {
                t = "● ทำงานกับแอปเพลง " + apps + " แอป";
            } else {
                t = "● พร้อมแล้ว เปิดเพลงในแอปเพลงได้เลย (ถ้ากำลังเล่นอยู่ ให้หยุดแล้วเล่นใหม่)";
            }
            if (prefs.maxOn()) t += s.maxSupported() ? " · โหมด MAX เปิดอยู่" : " · เครื่องนี้ใช้โหมด MAX ไม่ได้";
            if (prefs.calibOn() && s.maxSupported() && CalibMath.decode(prefs.calib()) != null) t += " · แก้เสียงตามที่วัดแล้ว";
            status.setText(t);
            status.setTextColor(ACCENT);
        }
    }

    // ================= ตัวช่วยวาด =================

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, LINE, 16));
        parent.addView(c, lp(0, 0, 0, 0));
        return c;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s.toUpperCase(), 13, MUTED);
        t.setLetterSpacing(0.06f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(lp(dp(4), dp(22), 0, dp(8)));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(l, t, r, b);
        return p;
    }

    private String signedDb(int mB) {
        int db = Math.round(mB / 100f);
        return (db > 0 ? "+" : "") + db + " dB";
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
