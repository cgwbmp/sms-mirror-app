package com.example.smsmirror.core

import android.content.Context
import com.example.smsmirror.data.AppDatabase
import com.example.smsmirror.data.OutboxDao
import com.example.smsmirror.data.RuleDao
import com.example.smsmirror.mail.SmtpClient
import com.example.smsmirror.security.SecureStore

/**
 * Single place the app's collaborators are built.
 *
 * A BroadcastReceiver and a Worker are both entered by the system with nothing but a
 * Context, so they resolve dependencies through here rather than being injected.
 */
class AppContainer private constructor(context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.build(context) }
    val secrets: SecureStore by lazy { SecureStore(context) }
    val settings: AppSettings by lazy { AppSettings(context, secrets) }
    val mailer: SmtpClient by lazy { SmtpClient() }

    val outbox: OutboxDao get() = database.outbox()
    val rules: RuleDao get() = database.rules()

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
