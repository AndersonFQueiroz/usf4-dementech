package com.dementech.update;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class Bar {

    private Bar() {
    }

    public static void attach(final Activity act) {
        final Context app = act.getApplicationContext();
        if (Downloader.dataPresent(app)) return;

        final ViewGroup content = act.findViewById(android.R.id.content);
        if (content == null) return;
        if (content.findViewWithTag("dementech-bar") != null) return;

        float d = act.getResources().getDisplayMetrics().density;
        int pad = (int) (18 * d);
        int margin = (int) (14 * d);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("dementech-bar");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 10, 10, 12));
        bg.setCornerRadius(8 * d);
        bg.setStroke((int) d, Color.argb(40, 255, 255, 255));
        box.setBackground(bg);
        box.setPadding(pad, (int) (12 * d), pad, (int) (14 * d));

        final TextView label = new TextView(act);
        label.setTextColor(Color.rgb(225, 228, 234));
        label.setTextSize(11);
        label.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        label.setLetterSpacing(0.06f);
        label.setText("BAIXANDO DADOS DO JOGO · conectando");
        box.addView(label);

        final ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255, 59, 78)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 34, 38)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (4 * d));
        lp.topMargin = (int) (9 * d);
        box.addView(bar, lp);

        final FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        flp.leftMargin = margin;
        flp.rightMargin = margin;
        flp.bottomMargin = (int) (26 * d);
        content.addView(box, flp);

        Downloader.uiActive = true;
        Downloader.resume(app);

        final Handler ui = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (act.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed())) {
                    return;
                }
                long done = Downloader.done;
                long total = Downloader.total;
                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
                if ("erro".equals(Downloader.state)) {
                    label.setText("FALHOU · toque para tentar de novo");
                    box.setOnClickListener(v -> Downloader.start(app));
                } else if ("concluido".equals(Downloader.state)) {
                    label.setText("DADOS PRONTOS · abrindo o jogo");
                    Toast.makeText(act, "Dados do jogo prontos", Toast.LENGTH_SHORT).show();
                    Downloader.uiActive = false;
                    ui.removeCallbacks(this);
                    content.removeView(box);
                    act.recreate();
                    return;
                } else {
                    label.setText("BAIXANDO DADOS DO JOGO · " + pct + "% · "
                            + fmt(done) + " de " + fmt(total));
                }
                ui.postDelayed(this, 500);
            }
        };
        ui.post(tick);
    }

    private static String fmt(long bytes) {
        if (bytes <= 0) return "0 MB";
        double g = bytes / 1073741824.0;
        if (g >= 1) return String.format("%.2f GB", g);
        return String.format("%.0f MB", bytes / 1048576.0);
    }
}
