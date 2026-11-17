package com.meumonstrinho.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Reminders.evaluate(context);
        Reminders.scheduleNext(context);
    }
}
