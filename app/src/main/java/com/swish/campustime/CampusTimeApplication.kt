package com.swish.campustime

import android.app.Application
import com.swish.campustime.reminder.ReminderScheduler

class CampusTimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
    }
}
