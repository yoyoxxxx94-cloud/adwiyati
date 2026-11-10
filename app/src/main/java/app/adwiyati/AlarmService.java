package app.adwiyati;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Plays the app's own chime and then says the message out loud (Arabic voice),
 * again and again until the user presses "done" or 90 seconds pass.
 * Label format: shownText \u0001 spokenText \u0001 kind (med | appt)
 */
public class AlarmService extends Service {
    static final String STOP = "app.adwiyati.STOP";
    private static final String CHANNEL = "adwiyati_alarm";
    private MediaPlayer player;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Vibrator vib;
    private String speech = "حان موعد الدواء";
    private String kind = "med";
    private boolean running = false;
    private final Handler handler = new Handler();
    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };
    private final Runnable skip = new Runnable() {
        @Override
        public void run() {
            try {
                if (player != null) player.stop();
            } catch (Exception ignored) {
            }
            speakOrWait();
        }
    };
    private final Runnable cycle = new Runnable() {
        @Override
        public void run() {
            playCycle();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String raw = intent != null ? intent.getStringExtra("label") : null;
        if (raw == null) raw = "حان موعد الدواء";
        String[] parts = raw.split("\u0001", -1);
        String label = parts[0];
        if (parts.length > 1 && parts[1].length() > 0) speech = parts[1];
        if (parts.length > 2 && parts[2].length() > 0) kind = parts[2];

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "تنبيهات الدواء",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }

        Intent act = new Intent(this, AlarmActivity.class);
        act.putExtra("label", label);
        act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(this, 1, act,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("أدويتي")
                .setContentText(label)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "تم", stopPi).build());
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(77, b.build());
        }

        releaseSound();
        running = true;
        startVibration();
        initTts();
        playCycle();
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, 90000);
        return START_NOT_STICKY;
    }

    private AudioAttributes alarmAttrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private void initTts() {
        ttsReady = false;
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS || tts == null) return;
                    try {
                        int r = tts.setLanguage(new Locale("ar"));
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            return;
                        }
                        tts.setAudioAttributes(alarmAttrs());
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String id) {
                            }

                            @Override
                            public void onDone(String id) {
                                nextCycle();
                            }

                            @Override
                            public void onError(String id) {
                                nextCycle();
                            }
                        });
                        ttsReady = true;
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void nextCycle() {
        if (!running) return;
        handler.removeCallbacks(cycle);
        handler.postDelayed(cycle, 2500);
    }

    private void playCycle() {
        if (!running) return;
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            String src = AlarmHelper.soundSrc(this, kind);
            if ("voice".equals(src)) {
                speakOrWait();
                return;
            }
            int sid = ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).generateAudioSessionId();
            if ("device".equals(src)) {
                String u = AlarmHelper.soundUri(this, kind);
                if (u.length() > 0) {
                    try {
                        player = MediaPlayer.create(this, android.net.Uri.parse(u), null, alarmAttrs(), sid);
                    } catch (Exception ignored) {
                        player = null;
                    }
                }
            }
            if (player == null) {
                player = MediaPlayer.create(this, AlarmHelper.toneRes(AlarmHelper.soundTone(this, kind)),
                        alarmAttrs(), sid);
            }
            if (player == null) {
                speakOrWait();
                return;
            }
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    handler.removeCallbacks(skip);
                    speakOrWait();
                }
            });
            player.start();
            handler.removeCallbacks(skip);
            handler.postDelayed(skip, 9000);
        } catch (Exception e) {
            speakOrWait();
        }
    }

    private void speakOrWait() {
        if (!running) return;
        if (ttsReady && tts != null) {
            try {
                Bundle p = new Bundle();
                p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, p, "adw");
                return;
            } catch (Exception ignored) {
            }
        }
        nextCycle();
    }

    private void startVibration() {
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 900}, 0));
        } catch (Exception ignored) {
        }
    }

    private void releaseSound() {
        running = false;
        handler.removeCallbacks(cycle);
        handler.removeCallbacks(skip);
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {
        }
        tts = null;
        ttsReady = false;
        try {
            if (vib != null) vib.cancel();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        releaseSound();
        sendBroadcast(new Intent(AlarmActivity.CLOSE).setPackage(getPackageName()));
        super.onDestroy();
    }
}
