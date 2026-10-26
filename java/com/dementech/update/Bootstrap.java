package com.dementech.update;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Bootstrap {

    public static final String MANIFEST_URL =
            "https://github.com/AndersonFQueiroz/usf4-dementech/releases/latest/download/manifest.json";

    private static final String PREFS = "dementech_update";
    private static final String K_VER = "patch_ver";
    private static final String CH = "dementech";

    private Bootstrap() {
    }

    public static void check(Activity act) {
        final Context app = act.getApplicationContext();
        new Thread(() -> {
            try {
                run(app);
            } catch (Throwable t) {
                log(app, "falha: " + t);
            }
        }, "dementech-update").start();
    }

    private static void run(Context ctx) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
        int local = sp.getInt(K_VER, 0);

        String body = new String(fetch(MANIFEST_URL), "UTF-8");
        JSONObject m = new JSONObject(body);
        int remote = m.optInt("version", 0);

        JSONObject patch = m.optJSONObject("patch");
        if (patch != null && remote > local) {
            String url = patch.getString("url");
            File tmp = new File(ctx.getCacheDir(), "patch.zip");
            startKeepAlive(ctx, "Atualizando GTA IV Dementech");
            try {
                download(url, tmp, patch.optLong("size", 0), (done, total) -> {
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        KeepAlive.progress(pct, "baixando atualização… " + pct + "%");
                    }
                });
                String sha = patch.optString("sha256", "");
                if (!sha.isEmpty() && !sha.equalsIgnoreCase(sha256(tmp))) {
                    throw new IOException("sha256 não confere");
                }
                unzip(tmp, ctx.getFilesDir());
                sp.edit().putInt(K_VER, remote).apply();
                writeText(new File(ctx.getFilesDir(), "patch_status.txt"),
                        "patch " + remote + " aplicado\n");
                notify(ctx, "Dementech USF4 atualizado", "patch " + remote);
                log(ctx, "patch aplicado " + remote);
            } finally {
                stopKeepAlive(ctx);
                tmp.delete();
            }
        }

        JSONArray data = m.optJSONArray("data");
        File root = ctx.getExternalFilesDir(null);
        if (data != null && root != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject d = data.getJSONObject(i);
                String url = d.optString("url", "");
                if (url.isEmpty()) continue;
                boolean tar = d.optBoolean("tar", false) || url.endsWith(".tar.gz");
                File dest = new File(root, d.optString("dir", "UsfivRecomp"));
                if (d.optBoolean("done", false)) continue;
                File marker = new File(root, ".dados_ok");
                if (marker.isFile()) continue;
                startKeepAlive(ctx, "Baixando dados do jogo");
                try {
                    if (tar) {
                        downloadTarGz(url, dest, d.optLong("size", 0), (done, total) -> {
                            if (total > 0) {
                                int pct = (int) (done * 100 / total);
                                KeepAlive.progress(pct, "baixando dados… " + pct + "%");
                            }
                        });
                    } else {
                        File z = new File(ctx.getCacheDir(), "data.pack");
                        download(url, z, d.optLong("size", 0), (done, total) -> { });
                        unzip(z, dest);
                        z.delete();
                    }
                    writeText(marker, "ok\n");
                    notify(ctx, "Dados do jogo prontos", "Dementech USF4");
                } finally {
                    stopKeepAlive(ctx);
                }
            }
        }
    }

    public interface Progress {
        void on(long done, long total);
    }

    private static void startKeepAlive(Context ctx, String title) {
        try {
            Intent i = new Intent(ctx, KeepAlive.class);
            i.putExtra("title", title);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    private static void stopKeepAlive(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, KeepAlive.class));
        } catch (Throwable ignored) {
        }
    }

    static byte[] fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DementechLauncher");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, File out, long expected, Progress p) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DementechLauncher");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long total = expected > 0 ? expected : c.getContentLengthLong();
            InputStream in = new BufferedInputStream(c.getInputStream(), 1 << 16);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16);
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                p.on(done, total);
            }
            os.flush();
            os.close();
            in.close();
        } finally {
            c.disconnect();
        }
    }

    static void downloadTarGz(String url, File dest, long expected, Progress p) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DementechLauncher");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long total = expected > 0 ? expected : c.getContentLengthLong();
            dest.mkdirs();
            Counting in = new Counting(new GZIPInputStream(new BufferedInputStream(c.getInputStream(), 1 << 16), 1 << 16));
            in.progress = p;
            in.total = total;
            untar(in, dest, dest.getCanonicalPath());
        } finally {
            c.disconnect();
        }
    }

    private static final class Counting extends InputStream {
        final InputStream in;
        long done;
        long total;
        Progress progress;

        Counting(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) tick(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) tick(n);
            return n;
        }

        private void tick(int n) {
            done += n;
            if (progress != null) progress.on(done, total);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static void untar(InputStream in, File dest, String root) throws IOException {
        byte[] h = new byte[512];
        while (true) {
            if (!readFully(in, h, 512)) return;
            boolean zero = true;
            for (byte b : h) {
                if (b != 0) {
                    zero = false;
                    break;
                }
            }
            if (zero) return;
            String name = str(h, 0, 100);
            if (untarLongName != null) {
                name = untarLongName;
                untarLongName = null;
            }
            long size = octal(h, 124, 12);
            char type = (char) (h[156] & 0xFF);
            String prefix = str(h, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            String link = str(h, 157, 100);
            long pad = (512 - (size % 512)) % 512;
            File out = new File(dest, name);
            if (!out.getCanonicalPath().startsWith(root)) {
                skipFully(in, size + pad);
                continue;
            }
            if (type == '5') {
                out.mkdirs();
                skipFully(in, pad);
            } else if (type == '0' || type == 0 || type == '7') {
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16);
                copyN(in, os, size);
                os.close();
                skipFully(in, pad);
            } else if (type == 'L') {
                byte[] nb = new byte[(int) size];
                readFully(in, nb, (int) size);
                skipFully(in, pad);
                untarLongName = new String(nb, "UTF-8").replace("\u0000", "").trim();
            } else if (type == 'x' || type == 'g') {
                skipFully(in, size + pad);
            } else if (type == '1' || type == '2') {
                skipFully(in, size + pad);
            } else {
                skipFully(in, size + pad);
            }
        }
    }

    private static String untarLongName;

    private static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] b = new byte[1 << 16];
        long left = n;
        while (left > 0) {
            int r = in.read(b, 0, (int) Math.min(b.length, left));
            if (r < 0) throw new IOException("tar truncado");
            out.write(b, 0, r);
            left -= r;
        }
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long s = in.skip(left);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            left -= s;
        }
    }

    private static boolean readFully(InputStream in, byte[] b, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) return false;
            off += r;
        }
        return true;
    }

    private static String str(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off);
    }

    private static long octal(byte[] b, int off, int len) {
        String s = str(b, off, len).trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void unzip(File zip, File dest) throws IOException {
        String root = dest.getCanonicalPath();
        ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        byte[] buf = new byte[1 << 16];
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            File out = new File(dest, e.getName());
            if (!out.getCanonicalPath().startsWith(root)) continue;
            if (e.isDirectory()) {
                out.mkdirs();
            } else {
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                OutputStream os = new BufferedOutputStream(new FileOutputStream(out));
                int n;
                while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
            }
            z.closeEntry();
        }
        z.close();
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        byte[] b = new byte[1 << 16];
        int n;
        while ((n = in.read(b)) > 0) md.update(b, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[1 << 13];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private static void writeText(File f, String s) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(s.getBytes("UTF-8"));
        o.close();
    }

    private static void log(Context ctx, String msg) {
        android.util.Log.i("Dementech", msg);
        try {
            File f = new File(ctx.getFilesDir(), "update_log.txt");
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((System.currentTimeMillis() + " " + msg + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) {
        }
    }

    private static void notify(Context ctx, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CH, "Atualizações",
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CH) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true);
            nm.notify(2001, b.build());
        } catch (Throwable ignored) {
        }
    }

    public static final class KeepAlive extends Service {
        private static KeepAlive current;
        private PowerManager.WakeLock lock;

        @Override
        public void onCreate() {
            super.onCreate();
            current = this;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.createNotificationChannel(new NotificationChannel("dementech_dl", "Downloads",
                            NotificationManager.IMPORTANCE_LOW));
                }
            }
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dementech:update");
                lock.setReferenceCounted(false);
                lock.acquire(60 * 60 * 1000L);
            }
            Notification n = build("Dementech", "preparando…", -1);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(2002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(2002, n);
            }
        }

        private Notification build(String title, String text, int pct) {
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "dementech_dl") : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            if (pct >= 0) b.setProgress(100, pct, false);
            return b.build();
        }

        static void progress(int pct, String text) {
            KeepAlive s = current;
            if (s == null) return;
            NotificationManager nm = (NotificationManager) s.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(2002, s.build("Dementech", text, pct));
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return START_NOT_STICKY;
        }

        @Override
        public void onDestroy() {
            current = null;
            if (lock != null && lock.isHeld()) lock.release();
            stopForeground(true);
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
