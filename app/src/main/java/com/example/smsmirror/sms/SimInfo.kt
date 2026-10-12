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

    /**
     * Runs inside the SMS broadcast, so it must never throw: some OEM builds raise more
     * than SecurityException without the Phone permission, and an escaping exception
     * would drop the message. Missing details just make the forward say less.
     */
    @SuppressLint("MissingPermission") // guarded by the catch-all below
    fun describe(context: Context, subscriptionId: Int): String {
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return UNKNOWN
            val info = manager.getActiveSubscriptionInfo(subscriptionId) ?: return UNKNOWN
            // The number needs READ_PHONE_NUMBERS on top of READ_PHONE_STATE; without it
            // the slot/carrier label is still worth showing.
            val number = try {
                phoneNumber(manager, info, subscriptionId)
            } catch (e: Exception) {
                null
            }
            number ?: slotLabel(info)
        } catch (e: Exception) {
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
