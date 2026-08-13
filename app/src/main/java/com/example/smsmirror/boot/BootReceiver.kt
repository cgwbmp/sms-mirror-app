package com.example.smsmirror.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smsmirror.core.AppContainer
import com.example.smsmirror.work.ForwardWorker

/**
 * SmsReceiver is manifest-registered so it re-arms itself after a reboot. What this
 * handles is the queue: anything that was still pending when the device went down gets
 * flushed once it's back up.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (AppContainer.get(context).settings.forwardingEnabled) {
                    ForwardWorker.enqueue(context.applicationContext)
                }
            }
        }
    }
}
