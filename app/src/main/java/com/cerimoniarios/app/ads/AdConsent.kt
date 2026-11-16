package com.cerimoniarios.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Google's consent form (UMP); required before requesting ads where privacy laws ask for it. */
object AdConsent {
    fun gather(activity: Activity, onComplete: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { onComplete() } },
            { onComplete() },
        )
    }

    fun canRequestAds(context: Context) = UserMessagingPlatform.getConsentInformation(context).canRequestAds()

    fun isPrivacyOptionsRequired(context: Context) =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
}
