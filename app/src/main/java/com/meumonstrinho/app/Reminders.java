package com.meumonstrinho.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Lembretes do app. A cada ~15 minutos o sistema acorda o ReminderReceiver,
 * que confere quais horários passaram desde a última checagem e mostra as notificações.
 */
public final class Reminders {

    public static final String PREFS = "monstrinho";
    private static final String CHANNEL = "lembretes";
    private static final long TICK = 15L * 60L * 1000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private Reminders() {
    }

    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TICK, pi);
    }

    public static void evaluate(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String remJson = p.getString("rem", null);
        long now = System.currentTimeMillis();
        long last = p.getLong("lastTick", 0);
        if (last == 0 || now - last > 3 * HOUR || last > now) last = now - TICK;
        p.edit().putLong("lastTick", now).apply();
        if (remJson == null) return;

        try {
            JSONObject rem = new JSONObject(remJson);
            JSONObject status = new JSONObject(p.getString("status", "{}"));
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
            boolean statusToday = today.equals(status.optString("date"));
            JSONObject phrases = rem.optJSONObject("phrases");

            // Refeições
            JSONObject meals = rem.optJSONObject("meals");
            if (meals != null && meals.optBoolean("on")) {
                JSONArray times = meals.optJSONArray("times");
                if (times != null) {
                    for (int i = 0; i < times.length(); i++) {
                        long at = todayAt(now, times.optString(i));
                        if (at > last && at <= now) {
                            notify(c, 100 + i, "Hora de comer!", pick(phrases, "meal", "Hora de alimentar o seu monstrinho."));
                        }
                    }
                }
                long lastFood = status.optLong("lastFood", 0);
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                if (meals.optBoolean("fast") && lastFood > 0 && hour >= 9 && hour < 22
                        && now - lastFood > 4 * HOUR && p.getLong("fastFor", 0) != lastFood) {
                    p.edit().putLong("fastFor", lastFood).apply();
                    notify(c, 150, "Faz mais de 4 horas sem comer", "Eu tô com fome e quero merendar! Que tal um lanchinho com proteína?");
                }
            }

            // Água
            JSONObject water = rem.optJSONObject("water");
            if (water != null && water.optBoolean("on")) {
                boolean done = statusToday && status.optInt("waterMl", 0) >= status.optInt("waterGoal", 1);
                int every = Math.max(30, water.optInt("every", 120));
                int from = minutes(water.optString("from", "08:00"));
                int to = minutes(water.optString("to", "21:00"));
                if (!done) {
                    for (int m = from; m <= to; m += every) {
                        long at = todayAtMinutes(now, m);
                        if (at > last && at <= now) {
                            notify(c, 200, "Bora beber água?", pick(phrases, "water", "Um copinho de água já me deixa feliz."));
                            break;
                        }
                    }
                }
            }

            // Proteína
            JSONObject protein = rem.optJSONObject("protein");
            if (protein != null && protein.optBoolean("on")) {
                long at = todayAt(now, protein.optString("at", "16:00"));
                boolean ok = statusToday && status.optDouble("proteinPct", 0) >= 0.5;
                if (at > last && at <= now && !ok) {
                    notify(c, 300, "Prioridade: proteína", pick(phrases, "protein", "Que tal um whey, iogurte ou ovos?"));
                }
            }

            // Monjaro
            JSONArray mj = rem.optJSONArray("mj");
            if (mj != null) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                for (int i = 0; i < mj.length(); i++) {
                    JSONObject e = mj.optJSONObject(i);
                    if (e == null) continue;
                    Date d = f.parse(e.optString("at"));
                    if (d == null) continue;
                    long at = d.getTime();
                    if (at > last && at <= now) {
                        notify(c, 400 + i, e.optString("title", "Monjaro"), e.optString("body", ""));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String pick(JSONObject phrases, String key, String fallback) {
        if (phrases == null) return fallback;
        JSONArray a = phrases.optJSONArray(key);
        if (a == null || a.length() == 0) return fallback;
        return a.optString(new Random().nextInt(a.length()), fallback);
    }

    private static int minutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static long todayAt(long now, String hhmm) {
        int m = minutes(hhmm);
        if (m < 0) return -1;
        return todayAtMinutes(now, m);
    }

    private static long todayAtMinutes(long now, int m) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, m / 60);
        cal.set(Calendar.MINUTE, m % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void notify(Context c, int id, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Refeições, água, proteína e Monjaro");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFE99BBA)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(id, n);
    }
}
