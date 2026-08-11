package com.wormx.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.Calendar

/**
 * Caps interstitials at [MAX_ADS_PER_DAY] (default 2) and only offers to show
 * one after a "natural break" — a batch of completed downloads — never in the
 * middle of starting a download or opening the vault.
 *
 * The counter resets automatically at local midnight.
 */
class AdFrequencyManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("wormx_ad_prefs", Context.MODE_PRIVATE)
    private var interstitialAd: InterstitialAd? = null

    companion object {
        private const val MAX_ADS_PER_DAY = 2
        private const val DOWNLOADS_BETWEEN_ADS = 3 // natural-break cadence
        private const val KEY_SHOWN_COUNT = "shown_count"
        private const val KEY_LAST_RESET_DAY = "last_reset_day"
        private const val KEY_COMPLETED_SINCE_LAST_AD = "completed_since_last_ad"
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // swap for prod ID
    }

    private fun resetIfNewDay() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt(KEY_LAST_RESET_DAY, -1)
        if (today != lastDay) {
            prefs.edit()
                .putInt(KEY_SHOWN_COUNT, 0)
                .putInt(KEY_LAST_RESET_DAY, today)
                .apply()
        }
    }

    private fun shownToday(): Int {
        resetIfNewDay()
        return prefs.getInt(KEY_SHOWN_COUNT, 0)
    }

    /** Call once for every download that finishes; returns true if this was a good moment to show an ad. */
    fun onDownloadCompleted(): Boolean {
        val completed = prefs.getInt(KEY_COMPLETED_SINCE_LAST_AD, 0) + 1
        prefs.edit().putInt(KEY_COMPLETED_SINCE_LAST_AD, completed).apply()

        val quotaLeft = shownToday() < MAX_ADS_PER_DAY
        val atNaturalBreak = completed >= DOWNLOADS_BETWEEN_ADS
        return quotaLeft && atNaturalBreak
    }

    fun preload() {
        InterstitialAd.load(
            context, TEST_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
            }
        )
    }

    /** Shows the preloaded interstitial if the daily quota allows it, then re-arms the cadence counter. */
    fun showIfAllowed(activity: Activity) {
        if (shownToday() >= MAX_ADS_PER_DAY) return
        val ad = interstitialAd ?: return

        ad.show(activity)
        prefs.edit()
            .putInt(KEY_SHOWN_COUNT, shownToday() + 1)
            .putInt(KEY_COMPLETED_SINCE_LAST_AD, 0)
            .apply()
        interstitialAd = null
        preload() // ready for next time
    }
}
