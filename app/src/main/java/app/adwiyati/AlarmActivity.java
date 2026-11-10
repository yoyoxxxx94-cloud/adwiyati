package app.adwiyati;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full screen "time for your medicine" screen shown over the lock screen. */
public class AlarmActivity extends Activity {
    static final String CLOSE = "app.adwiyati.CLOSE_ALARM";
    private final BroadcastReceiver closer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        String label = getIntent().getStringExtra("label");
        if (label == null) label = "حان وقت الدواء";
        label = label.split("\u0001", -1)[0];

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0f766e"));
        root.setPadding(48, 48, 48, 48);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t1 = new TextView(this);
        t1.setText("حان وقت الدواء");
        t1.setTextColor(Color.parseColor("#fda4af"));
        t1.setTextSize(22);
        t1.setGravity(Gravity.CENTER);
        root.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(label);
        t2.setTextColor(Color.WHITE);
        t2.setTextSize(32);
        t2.setTypeface(Typeface.DEFAULT_BOLD);
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(0, 40, 0, 80);
        root.addView(t2);

        Button ok = new Button(this);
        ok.setText("تم، إيقاف التنبيه");
        ok.setTextSize(22);
        ok.setTextColor(Color.parseColor("#0f766e"));
        ok.setBackgroundColor(Color.WHITE);
        ok.setPadding(60, 40, 60, 40);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent s = new Intent(AlarmActivity.this, AlarmService.class);
                s.setAction(AlarmService.STOP);
                startService(s);
                finish();
            }
        });
        root.addView(ok);
        setContentView(root);

        IntentFilter f = new IntentFilter(CLOSE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(closer, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(closer, f);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(closer);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
