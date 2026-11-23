package com.oyun.yorunge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/**
 * Günlük hatırlatma bildirimleri.
 * Her akşam 19:00'da çalışır; o gün oyuna girilmediyse oyunun durumuna göre
 * (seri, haftalık meydan okuma, görevler, mağaza, rekor...) tek bir bildirim gösterir.
 * Oyun durumu JavaScript tarafından {@link #saveState} ile buraya aktarılır.
 * Telefon yeniden başlayınca alarmı tekrar kurar.
 */
public class NotifReceiver extends BroadcastReceiver {

    static final String PREFS = "yorunge_notif";
    static final String CHANNEL = "yorunge_hatirlatma";
    static final String ACTION_REMIND = "com.oyun.yorunge.REMIND";
    private static final int REMIND_HOUR = 19;
    private static final int NOTIF_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REMIND.equals(intent.getAction())) maybeNotify(context);
        schedule(context);
        YorungeWidget.refreshAll(context); // yeni gün: widget'taki "günlük ödül" durumunu tazele // hem alarmdan sonra hem telefon açılışında bir sonrakini kur
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Yerel saate göre gün numarası (oyundaki günlük ödülle aynı hesap). */
    static long dayIdx(long ms) {
        return (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L;
    }

    static void markOpened(Context c) {
        prefs(c).edit().putLong("lastOpen", System.currentTimeMillis()).apply();
    }

    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean("enabled", true);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).apply();
        schedule(c);
    }

    /** Oyundan gelen durum (JSON): streak, best, missions, coins, shopName, shopPrice, wkName, wkEm, wkDone. */
    static void saveState(Context c, String json) {
        prefs(c).edit().putString("state", json).apply();
    }

    private static PendingIntent remindIntent(Context c) {
        Intent i = new Intent(c, NotifReceiver.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Bir sonraki 19:00 için (kesin olmayan, pil dostu) alarm kurar. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = remindIntent(c);
        am.cancel(pi);
        if (!isEnabled(c)) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, REMIND_HOUR);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60_000L) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    /** Duruma göre gösterilecek {başlık, metin}; gösterilecek bir şey yoksa null. */
    static String[] pickMessage(Context c, long now) {
        long today = dayIdx(now);
        long lastOpenDay = dayIdx(prefs(c).getLong("lastOpen", now));
        if (lastOpenDay >= today) return null; // bugün zaten oynadı, rahatsız etme
        long away = today - lastOpenDay;

        JSONObject s;
        try { s = new JSONObject(prefs(c).getString("state", "{}")); } catch (Exception e) { s = new JSONObject(); }
        int streak = s.optInt("streak", 0);
        int best = s.optInt("best", 0);
        int missions = s.optInt("missions", 0);
        int coins = s.optInt("coins", 0);
        String shopName = s.optString("shopName", "");
        int shopPrice = s.optInt("shopPrice", 0);
        String wkName = s.optString("wkName", "");
        String wkEm = s.optString("wkEm", "📆");
        boolean wkDone = s.optBoolean("wkDone", false);

        // 1) Dün girdiyse ve serisi varsa: seri bozulmak üzere (en önemli)
        if (away == 1 && streak >= 2) {
            return new String[]{"🔥 " + streak + " günlük serin bozulmak üzere!",
                    "Bugün girip günlük ödülünü al, serin devam etsin."};
        }
        // 2) Pazartesi: yeni haftalık meydan okuma
        Calendar cal = Calendar.getInstance();
        boolean monday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
        if (monday && !wkName.isEmpty()) {
            return new String[]{wkEm + " Yeni haftalık meydan okuma!",
                    "Bu haftanın kuralı: " + wkName + ". Hedefe ulaş, +300 ✦ kap!"};
        }

        // 3) Diğer adaylar arasından rastgele seç
        List<String[]> pool = new ArrayList<>();
        if (away >= 3) {
            pool.add(new String[]{"⭐ Yıldızlar seni bekliyor", "Yörüngen " + away + " gündür boş, bir tur atmaya ne dersin?"});
            pool.add(new String[]{"🌑 Kara Güneş seni özledi", "Geri dön ve ona kim olduğunu göster 🚀"});
            pool.add(new String[]{"☄️ Uzay sessiz kaldı", "Kuyruklu yıldızlar seni bekliyor, hadi bir tur!"});
        }
        if (missions > 0) {
            pool.add(new String[]{"🎯 " + missions + " görev ödülün seni bekliyor", "Görevler'e girip ✦ ödüllerini topla!"});
        }
        if (!shopName.isEmpty() && shopPrice > 0 && coins >= shopPrice) {
            pool.add(new String[]{"🛒 " + shopName + " yıldızı seni bekliyor", "Almaya yetecek " + coins + " ✦'n var!"});
        }
        if (!wkName.isEmpty() && !wkDone) {
            pool.add(new String[]{wkEm + " Haftalık meydan okuma devam ediyor", wkName + " · hedefe ulaşırsan +300 ✦"});
        }
        if (best > 0) {
            pool.add(new String[]{"🚀 Rekorun " + best, "Bugün kırabilir misin?"});
        }
        pool.add(new String[]{"🎁 Günlük ödülün hazır!", "Gel, ✦ ödülünü al" + (streak > 0 ? " ve serini büyüt 🔥" : "!")});
        pool.add(new String[]{"✨ Takımyıldızlar parlıyor", "5 yıldızı birden topla, bonus kap!"});

        return pool.get(new Random().nextInt(pool.size()));
    }

    @SuppressLint("MissingPermission") // izin aşağıda kontrol ediliyor
    static void maybeNotify(Context c) {
        if (!isEnabled(c)) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        String[] msg = pickMessage(c, System.currentTimeMillis());
        if (msg == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Günlük hatırlatmalar",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Günlük ödül, seri ve meydan okuma hatırlatmaları");
                nm.createNotificationChannel(ch);
            }
        }

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF7CF3FF)
                .setContentTitle(msg[0])
                .setContentText(msg[1])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg[1]))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
    }
}
