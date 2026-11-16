package com.cerimoniarios.app

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cerimoniarios.app.ads.AdConsent
import com.cerimoniarios.app.databinding.ActivitySupportBinding
import com.cerimoniarios.app.support.SupportState
import com.cerimoniarios.app.support.SupportTier
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SupportActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpSystemBars(binding.root, binding.appBar)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.adsSwitch.isChecked = app.preferences.adsEnabled.value
        binding.adsSwitch.setOnCheckedChangeListener { _, checked -> app.preferences.setAdsEnabled(checked) }
        binding.privacyButton.setOnClickListener { AdConsent.showPrivacyOptions(this) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.support.state.collect(::render) }
                launch {
                    app.support.messages.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.support.refresh()
        binding.privacyButton.isVisible = AdConsent.isPrivacyOptionsRequired(this)
    }

    private fun render(state: SupportState) {
        binding.supporterCard.isVisible = state.isSupporter
        binding.adsSection.isVisible = !state.isSupporter
        binding.tiersProgress.isVisible = state.connection == SupportState.Connection.Connecting

        val status = when {
            state.connection == SupportState.Connection.Unavailable -> getString(R.string.support_unavailable)
            state.connection == SupportState.Connection.Ready && state.tiers.isEmpty() -> getString(R.string.support_no_products)
            else -> null
        }
        binding.tiersStatus.isVisible = status != null
        binding.tiersStatus.text = status

        binding.tiers.removeAllViews()
        state.tiers.forEach { binding.tiers.addView(tierButton(it)) }
    }

    private fun tierButton(tier: SupportTier) = MaterialButton(this).apply {
        text = when {
            tier.owned -> getString(R.string.support_tier_owned, tier.price)
            tier.pending -> getString(R.string.support_tier_pending, tier.price)
            else -> getString(R.string.support_tier_buy, tier.price)
        }
        isEnabled = !tier.owned && !tier.pending
        setOnClickListener { app.support.purchase(this@SupportActivity, tier) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
    }
}
