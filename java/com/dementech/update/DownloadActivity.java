package com.dementech.update;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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

public class DownloadActivity extends Activity {

    private static final int BG = Color.rgb(8, 8, 10);
    private static final int TXT = Color.rgb(240, 242, 246);
    private static final int DIM = Color.rgb(150, 155, 165);
    private static final int ACCENT = Color.rgb(255, 59, 78);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView pct, sub, btn;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                }
            } catch (Throwable ignored) {
            }
        }
        askBattery();
        Downloader.resume(getApplicationContext());
        ui.post(tick);
    }

    private void askBattery() {
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                android.content.SharedPreferences sp =
                        getSharedPreferences("dementech_update", 0);
                if (!sp.getBoolean("asked_battery", false)) {
                    sp.edit().putBoolean("asked_battery", true).apply();
                    Intent i = new Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), dp(56), dp(28), dp(28));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = new TextView(this);
        brand.setText("D E M E N T E C H");
        brand.setTextColor(DIM);
        brand.setTextSize(10);
        brand.setLetterSpacing(0.42f);
        col.addView(brand);

        TextView title = new TextView(this);
        title.setText("DADOS DO JOGO");
        title.setTextColor(TXT);
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(18);
        col.addView(title, tp);

        TextView info = new TextView(this);
        info.setText("Ultra Street Fighter IV · 7,5 GB · download único");
        info.setTextColor(DIM);
        info.setTextSize(12);
        tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(6);
        col.addView(info, tp);

        LinearLayout spacer = new LinearLayout(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 0.55f));

        pct = new TextView(this);
        pct.setText("0%");
        pct.setTextColor(TXT);
        pct.setTextSize(52);
        pct.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        col.addView(pct);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(30, 30, 34)));
        tp = new LinearLayout.LayoutParams(-1, dp(6));
        tp.topMargin = dp(12);
        col.addView(bar, tp);

        sub = new TextView(this);
        sub.setText("aguardando");
        sub.setTextColor(DIM);
        sub.setTextSize(12);
        tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(10);
        col.addView(sub, tp);

        btn = new TextView(this);
        btn.setText("BAIXAR AGORA");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        btn.setLetterSpacing(0.08f);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(ACCENT);
        g.setCornerRadius(dp(6));
        btn.setBackground(g);
        btn.setOnClickListener(v -> {
            if (Downloader.running) {
                Toast.makeText(this, "Download em andamento", Toast.LENGTH_SHORT).show();
                return;
            }
            Downloader.start(getApplicationContext());
            tick.run();
        });
        tp = new LinearLayout.LayoutParams(-1, dp(52));
        tp.topMargin = dp(22);
        col.addView(btn, tp);

        LinearLayout spacer2 = new LinearLayout(this);
        col.addView(spacer2, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView back = new TextView(this);
        back.setText("fechar");
        back.setTextColor(DIM);
        back.setTextSize(12);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(16), 0, 0);
        back.setOnClickListener(v -> finish());
        col.addView(back, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long done = Downloader.done;
            long total = Downloader.total;
            int p = total > 0 ? (int) (done * 100 / total) : 0;
            pct.setText(p + "%");
            bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
            String st = Downloader.state;
            if (Downloader.running) {
                sub.setText(gb(done) + " de " + gb(total) + " · " + st);
                btn.setText("BAIXANDO…");
                btn.setAlpha(0.55f);
            } else if (Downloader.done == Downloader.total && total > 0 && "concluido".equals(st)) {
                sub.setText(gb(done) + " · concluído");
                btn.setText("JOGAR");
                btn.setAlpha(1f);
                btn.setOnClickListener(v -> {
                    Intent i = new Intent();
                    i.setClassName(DownloadActivity.this, "io.github.belmantegu.usfivrecomp.LauncherActivity");
                    try {
                        startActivity(i);
                    } catch (Throwable ignored) {
                    }
                    finish();
                });
            } else if ("erro".equals(st)) {
                sub.setText(Downloader.lastError == null ? "falhou" : Downloader.lastError);
                btn.setText("TENTAR DE NOVO");
                btn.setAlpha(1f);
            } else {
                sub.setText("aguardando");
                btn.setText("BAIXAR AGORA");
                btn.setAlpha(1f);
            }
            ui.removeCallbacks(this);
            ui.postDelayed(this, 400);
        }
    };

    private static String gb(long bytes) {
        if (bytes <= 0) return "0 MB";
        double g = bytes / 1073741824.0;
        if (g >= 1) return String.format("%.2f GB", g);
        return String.format("%.0f MB", bytes / 1048576.0);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
