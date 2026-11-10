package app.adwiyati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String apptId = intent.getStringExtra("id");
        if (apptId != null) {
            AlarmHelper.prefs(ctx).edit().remove("at:" + apptId).apply();
            String l = intent.getStringExtra("label");
            Intent sv = new Intent(ctx, AlarmService.class);
            sv.putExtra("label", l == null ? "موعد المستشفى" : l);
            ContextCompat.startForegroundService(ctx, sv);
            return;
        }
        int h = intent.getIntExtra("h", 0);
        int m = intent.getIntExtra("m", 0);
        String label = intent.getStringExtra("label");
        boolean daily = intent.getBooleanExtra("daily", false);
        if (label == null) label = "حان وقت الدواء";
        if (daily) {
            AlarmHelper.schedule(ctx, h, m, label, true);
        } else {
            AlarmHelper.prefs(ctx).edit().remove(AlarmHelper.idOf(h, m, label)).apply();
        }
        Intent s = new Intent(ctx, AlarmService.class);
        s.putExtra("label", label);
        ContextCompat.startForegroundService(ctx, s);
    }
}
