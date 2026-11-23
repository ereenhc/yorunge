package com.oyun.yorunge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * Ana ekran widget'ı: günlük ödül durumu, seri ve rekor.
 * Veriyi oyunun bildirimler için kaydettiği durumdan (NotifReceiver prefs) okur.
 * Oyun durumu değiştikçe ve 30 dakikada bir yenilenir.
 */
public class YorungeWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    /** Ekleli tüm widget'ları günceller (oyun durumu değişince çağrılır). */
    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, YorungeWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(c);
        for (int id : ids) m.updateAppWidget(id, v);
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_yorunge);

        JSONObject s;
        try { s = new JSONObject(NotifReceiver.prefs(c).getString("state", "{}")); } catch (Exception e) { s = new JSONObject(); }
        long today = NotifReceiver.dayIdx(System.currentTimeMillis());
        long drLast = s.optLong("drLast", -1);
        int streak = s.optInt("streak", 0);
        // seri dün ya da bugün alınmadıysa bozulmuştur
        if (drLast != today && drLast != today - 1) streak = 0;
        int best = s.optInt("best", 0);

        v.setTextViewText(R.id.w_daily, drLast == today ? "✅ Bugünkü ödül alındı" : "🎁 Günlük ödülün hazır!");
        v.setTextViewText(R.id.w_streak, streak > 0 ? "🔥 Seri: " + streak + " gün" : "🔥 Yeni seri başlat!");
        v.setTextViewText(R.id.w_best, "🚀 Rekor: " + best);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, pi);
        return v;
    }
}
