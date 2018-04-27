package com.example.facedoor.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.facedoor.LauncherActivity2;

public class BootReceiver extends BroadcastReceiver{
	
	private static final String ACTION = "android.intent.action.BOOT_COMPLETED";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(ACTION)) {
			Intent mainIntent = new Intent(context,LauncherActivity2.class);
			mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(mainIntent);
		}
	}
}
