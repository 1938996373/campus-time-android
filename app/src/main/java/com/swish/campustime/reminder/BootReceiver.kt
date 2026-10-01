package com.swish.campustime.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swish.campustime.data.AppDatabase
import com.swish.campustime.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ScheduleRepository(context, AppDatabase.get(context)).reschedule() }
            finally { pending.finish() }
        }
    }
}
