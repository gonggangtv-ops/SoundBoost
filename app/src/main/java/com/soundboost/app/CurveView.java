package com.soundboost.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** กราฟแท่งแสดงการแก้เสียงแต่ละช่วงความถี่ (เขียว = เพิ่ม, ส้ม = กดลง) */
final class CurveView extends View {
    private double[] values;
    private final Paint up = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint down = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    CurveView(Context c) {
        super(c);
        up.setColor(Color.parseColor("#3DDBB4"));
        down.setColor(Color.parseColor("#F5B94A"));
        axis.setColor(Color.parseColor("#3A3F4A"));
        axis.setStrokeWidth(2);
        label.setColor(Color.parseColor("#8C95A5"));
        label.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
    }

    void setValues(double[] v) { values = v; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        if (values == null) return;
        float w = getWidth(), h = getHeight();
        float labelH = label.getTextSize() + 6;
        float top = 4, bottom = h - labelH;
        // ช่วงที่แสดง +4 .. -10 dB
        float zeroY = top + (bottom - top) * (4f / 14f);
        float perDb = (bottom - top) / 14f;
        int n = values.length;
        float slot = w / n;
        cv.drawLine(0, zeroY, w, zeroY, axis);
        for (int i = 0; i < n; i++) {
            float v = (float) values[i];
            float x0 = i * slot + slot * 0.18f, x1 = (i + 1) * slot - slot * 0.18f;
            float y = zeroY - v * perDb;
            if (Math.abs(v) < 0.05f) continue;
            if (v > 0) cv.drawRect(x0, y, x1, zeroY, up);
            else cv.drawRect(x0, zeroY, x1, y, down);
        }
        // ป้ายความถี่ใต้กราฟ
        String[] marks = {"200", "1k", "5k"};
        double[] at = {200, 1000, 5000};
        for (int m = 0; m < marks.length; m++) {
            int best = 0;
            for (int i = 0; i < n; i++)
                if (Math.abs(CalibMath.CENTERS[i] - at[m]) < Math.abs(CalibMath.CENTERS[best] - at[m])) best = i;
            float cx = best * slot + slot / 2 - label.measureText(marks[m]) / 2;
            cv.drawText(marks[m], cx, h - 4, label);
        }
    }
}
