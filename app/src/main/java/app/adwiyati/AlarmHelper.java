package app.adwiyati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

/** Stores and schedules the medicine alarms (works offline, with the app closed). */
public class AlarmHelper {
    static final String PREFS = "adwiyati_alarms";
    static final String KEY_RING = "ringtone";
    static final String ACTION = "app.adwiyati.ALARM";

    static String idOf(int h, int m, String label) {
        return h + ":" + m + ":" + label;
    }

    static long nextTrigger(int h, int m) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis() + 1000) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    static PendingIntent pending(Context ctx, int h, int m, String label, boolean daily) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(ACTION);
        i.setData(Uri.parse("alarm://adwiyati/" + Uri.encode(idOf(h, m, label))));
        i.putExtra("h", h);
        i.putExtra("m", m);
        i.putExtra("label", label);
        i.putExtra("daily", daily);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx, int h, int m, String label, boolean daily) {
        try {
            JSONObject o = new JSONObject();
            o.put("h", h);
            o.put("m", m);
            o.put("label", label);
            o.put("daily", daily);
            prefs(ctx).edit().putString(idOf(h, m, label), o.toString()).apply();
        } catch (Exception ignored) {
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent show = new Intent(ctx, MainActivity.class);
        PendingIntent showPi = PendingIntent.getActivity(ctx, 0, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(nextTrigger(h, m), showPi),
                pending(ctx, h, m, label, daily));
    }

    static void cancel(Context ctx, int h, int m, String label) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, h, m, label, false));
        prefs(ctx).edit().remove(idOf(h, m, label)).apply();
    }

    /** Cancels every alarm whose label ends with ": name". */
    static int cancelByName(Context ctx, String name) {
        int n = 0;
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            if (KEY_RING.equals(e.getKey())) continue;
            try {
                JSONObject o = new JSONObject(String.valueOf(e.getValue()));
                if (o.has("at")) continue;
                String label = o.getString("label");
                if (label.split("\u0001", -1)[0].endsWith(": " + name)) {
                    cancel(ctx, o.getInt("h"), o.getInt("m"), label);
                    n++;
                }
            } catch (Exception ignored) {
            }
        }
        return n;
    }


    static PendingIntent pendingAt(Context ctx, String id, long at, String label) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(ACTION);
        i.setData(Uri.parse("alarm://adwiyati/at/" + Uri.encode(id)));
        i.putExtra("id", id);
        i.putExtra("at", at);
        i.putExtra("label", label);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** One time alarm at an exact date and time (hospital appointment reminders). */
    static void scheduleAt(Context ctx, String id, long at, String label) {
        if (at <= System.currentTimeMillis() + 1000) return;
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("at", at);
            o.put("label", label);
            prefs(ctx).edit().putString("at:" + id, o.toString()).apply();
        } catch (Exception ignored) {
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent show = new Intent(ctx, MainActivity.class);
        PendingIntent showPi = PendingIntent.getActivity(ctx, 0, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pendingAt(ctx, id, at, label));
    }

    static void cancelAt(Context ctx, String id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingAt(ctx, id, 0, ""));
        prefs(ctx).edit().remove("at:" + id).apply();
    }

    /** Cancels every appointment alarm whose id starts with prefix. */
    static int cancelAtPrefix(Context ctx, String prefix) {
        int n = 0;
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("at:" + prefix)) {
                cancelAt(ctx, k.substring(3));
                n++;
            }
        }
        return n;
    }


    // ---- sound choice (one for all medicine alarms, one for all appointment alarms) ----
    static SharedPreferences soundPrefs(Context ctx) {
        return ctx.getSharedPreferences("adwiyati_sound", Context.MODE_PRIVATE);
    }

    static String soundSrc(Context ctx, String kind) {
        return soundPrefs(ctx).getString(kind + "_src", "app");
    }

    static int soundTone(Context ctx, String kind) {
        return soundPrefs(ctx).getInt(kind + "_tone", "appt".equals(kind) ? 2 : 1);
    }

    static String soundUri(Context ctx, String kind) {
        return soundPrefs(ctx).getString(kind + "_uri", "");
    }

    static void setSound(Context ctx, String kind, String src, int tone) {
        soundPrefs(ctx).edit().putString(kind + "_src", src).putInt(kind + "_tone", tone).apply();
    }

    static void setSoundUri(Context ctx, String kind, Uri u) {
        soundPrefs(ctx).edit().putString(kind + "_uri", u == null ? "" : u.toString())
                .putString(kind + "_src", "device").apply();
    }

    static int toneRes(int n) {
        switch (n) {
            case 2: return R.raw.chime_appt;
            case 3: return R.raw.tone3;
            case 4: return R.raw.tone4;
            case 5: return R.raw.tone5;
            case 6: return R.raw.tone6;
            default: return R.raw.chime_med;
        }
    }

    static void rescheduleAll(Context ctx) {
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            if (KEY_RING.equals(e.getKey())) continue;
            try {
                JSONObject o = new JSONObject(String.valueOf(e.getValue()));
                if (o.has("at")) {
                    long at = o.getLong("at");
                    if (at > System.currentTimeMillis()) {
                        scheduleAt(ctx, o.getString("id"), at, o.getString("label"));
                    } else {
                        prefs(ctx).edit().remove(e.getKey()).apply();
                    }
                    continue;
                }
                schedule(ctx, o.getInt("h"), o.getInt("m"), o.getString("label"), o.getBoolean("daily"));
            } catch (Exception ignored) {
            }
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Uri ringtone(Context ctx) {
        String s = prefs(ctx).getString(KEY_RING, null);
        if (s != null && s.length() > 0) return Uri.parse(s);
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    static void saveRingtone(Context ctx, Uri u) {
        prefs(ctx).edit().putString(KEY_RING, u == null ? "" : u.toString()).apply();
    }
}
