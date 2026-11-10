package app.adwiyati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the alarms back after the phone restarts or the app is updated. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmHelper.rescheduleAll(ctx);
    }
}
