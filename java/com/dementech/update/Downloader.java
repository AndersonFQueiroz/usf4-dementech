package com.dementech.update;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Downloader {

    public static volatile boolean running;
    public static volatile long done;
    public static volatile long total;
    public static volatile String state = "parado";
    public static volatile String lastError;
    public static volatile boolean uiActive;

    public static boolean pending() {
        return uiActive;
    }

    private Downloader() {
    }

    static File root(Context ctx) {
        File f = ctx.getExternalFilesDir(null);
        return f != null ? f : ctx.getFilesDir();
    }

    static boolean dataPresent(Context ctx) {
        File r = root(ctx);
        if (!new File(r, ".dados_ok").isFile()) return false;
        File xex = new File(new File(r, "game"), "Default.xex");
        return xex.isFile() && xex.length() > 1048576;
    }

    public static void resume(Context ctx) {
        if (running) return;
        if (dataPresent(ctx)) {
            state = "concluido";
            return;
        }
        start(ctx);
    }

    public static synchronized void start(Context ctx) {
        if (running) return;
        final Context app = ctx.getApplicationContext();
        running = true;
        done = 0;
        total = 0;
        lastError = null;
        state = "conectando";
        startService(app);
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    Bootstrap.KeepAlive.progress(pct, "baixando dados… " + pct + "%");
                }
            }
        }, "dementech-notif").start();
        new Thread(() -> {
            try {
                run(app);
                state = "concluido";
            } catch (Exception e) {
                state = "erro";
                lastError = String.valueOf(e.getMessage());
                Bootstrap.log(app, "download falhou: " + e);
            } finally {
                running = false;
                stopService(app);
            }
        }, "dementech-data").start();
    }

    private static void run(Context ctx) throws Exception {
        JSONObject m = new JSONObject(new String(Bootstrap.fetch(Bootstrap.MANIFEST_URL), "UTF-8"));
        JSONArray data = m.optJSONArray("data");
        if (data == null || data.length() == 0) throw new Exception("manifesto sem dados");
        JSONObject d = data.getJSONObject(0);
        String listUrl = d.optString("list", "");
        if (listUrl.isEmpty()) throw new Exception("manifesto sem lista");
        String dir = d.optString("dir", "");
        if (dir.equals("UsfivRecomp")) dir = "";
        File dest = new File(root(ctx), dir);
        final boolean sync = d.optBoolean("sync", true);

        state = "lendo lista";
        JSONArray files = new JSONObject(new String(Bootstrap.fetch(listUrl), "UTF-8"))
                .getJSONArray("files");
        long sum = 0;
        for (int i = 0; i < files.length(); i++) sum += files.getJSONObject(i).optLong("s", 0);
        total = sum;

        final AtomicLong acc = new AtomicLong();
        final AtomicInteger idx = new AtomicInteger();
        final long[] active = new long[3];
        final AtomicInteger slot = new AtomicInteger();
        final AtomicReference<Exception> err = new AtomicReference<>();
        state = "baixando";

        Thread[] pool = new Thread[3];
        for (int w = 0; w < pool.length; w++) {
            final int me = w;
            pool[w] = new Thread(() -> {
                while (err.get() == null) {
                    int i = idx.getAndIncrement();
                    if (i >= files.length()) return;
                    try {
                        JSONObject f = files.getJSONObject(i);
                        String path = f.getString("p");
                        long size = f.optLong("s", 0);
                        String id = f.getString("id");
                        File out = new File(dest, path);
                        if (out.isFile() && out.length() == size) {
                            acc.addAndGet(size);
                            done = acc.get() + active[0] + active[1] + active[2];
                            continue;
                        }
                        File parent = out.getParentFile();
                        if (parent != null) parent.mkdirs();
                        File part = new File(out.getPath() + ".part");
                        String url = "https://drive.usercontent.google.com/download?id=" + id
                                + "&export=download&confirm=t";
                        int tries = 0;
                        while (true) {
                            try {
                                Bootstrap.download(url, part, size, (d2, t2) -> {
                                    active[me] = d2;
                                    done = acc.get() + active[0] + active[1] + active[2];
                                });
                                if (part.length() != size) throw new Exception("tamanho " + path);
                                if (!part.renameTo(out)) throw new Exception("rename " + path);
                                break;
                            } catch (Exception e) {
                                part.delete();
                                if (++tries >= 4) throw e;
                                Thread.sleep(1500L * tries);
                            }
                        }
                        active[me] = 0;
                        acc.addAndGet(size);
                        done = acc.get() + active[0] + active[1] + active[2];
                    } catch (Exception e) {
                        err.compareAndSet(null, e);
                        return;
                    }
                }
            }, "dementech-data" + w);
            pool[w].start();
        }
        for (Thread t : pool) t.join();
        Exception e = err.get();
        if (e != null) throw e;

        if (sync) {
            state = "limpando antigos";
            java.util.HashSet<String> keep = new java.util.HashSet<>();
            for (int i = 0; i < files.length(); i++) keep.add(files.getJSONObject(i).getString("p"));
            prune(dest, dest, keep);
        }
        Bootstrap.writeText(new File(root(ctx), ".dados_ok"), "ok\n");
    }

    private static void prune(File base, File dir, java.util.HashSet<String> keep) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        String rootPath = base.getAbsolutePath();
        for (File f : kids) {
            if (f.isDirectory()) {
                prune(base, f, keep);
                File[] rest = f.listFiles();
                if (rest != null && rest.length == 0) f.delete();
            } else {
                String rel = f.getAbsolutePath().substring(rootPath.length() + 1);
                if (rel.endsWith(".part") || !keep.contains(rel)) f.delete();
            }
        }
    }

    private static void startService(Context ctx) {
        try {
            Intent i = new Intent(ctx, Bootstrap.KeepAlive.class);
            i.putExtra("title", "Baixando dados do jogo");
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    private static void stopService(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, Bootstrap.KeepAlive.class));
        } catch (Throwable ignored) {
        }
    }
}
