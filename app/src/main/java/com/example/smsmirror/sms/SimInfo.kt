package com.example.smsmirror.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

/**
 * Describes which SIM received a message, for the "To" line of the forward.
 *
 * Carriers frequently leave the subscriber number unprovisioned, so this degrades to
 * a slot/carrier label rather than pretending it doesn't know.
 */
object SimInfo {

    @SuppressLint("MissingPermission") // guarded by the SecurityException catch below
    fun describe(context: Context, subscriptionId: Int): String {
        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: return UNKNOWN
        return try {
            val info = manager.getActiveSubscriptionInfo(subscriptionId) ?: return UNKNOWN
            phoneNumber(manager, info, subscriptionId) ?: slotLabel(info)
        } catch (e: SecurityException) {
            // READ_PHONE_STATE not granted — not fatal, the forward just says less.
            UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // SubscriptionInfo.number is the only option below API 33
    private fun phoneNumber(
        manager: SubscriptionManager,
        info: SubscriptionInfo,
        subscriptionId: Int,
    ): String? {
        val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPhoneNumber(subscriptionId)
        } else {
            info.number
        }
        return number?.takeIf { it.isNotBlank() }
    }

    private fun slotLabel(info: SubscriptionInfo): String {
        val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "unknown carrier"
        return "SIM ${info.simSlotIndex + 1} · $carrier"
    }

    private const val UNKNOWN = "this device"
}
