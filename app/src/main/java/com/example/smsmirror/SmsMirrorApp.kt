package com.example.smsmirror

import android.app.Application
import com.example.smsmirror.core.AppContainer
import com.example.smsmirror.core.Notifications
import com.example.smsmirror.work.ForwardWorker

class SmsMirrorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)

        val container = AppContainer.get(this)
        // Cheap, and it rolls the day over so Home never shows yesterday's tallies.
        container.settings.refresh()

        // Anything still queued from a previous process (crash, force-stop, no network
        // at the time) gets another attempt now.
        if (container.settings.forwardingEnabled) {
            ForwardWorker.enqueue(this)
        }
    }
}
