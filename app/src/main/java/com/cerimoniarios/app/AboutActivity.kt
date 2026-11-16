package com.cerimoniarios.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.cerimoniarios.app.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpSystemBars(binding.root, binding.appBar)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.supportButton.setOnClickListener { startActivity(Intent(this, SupportActivity::class.java)) }
        binding.privacyButton.setOnClickListener { openPrivacyPolicy() }
        binding.version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }

    private fun openPrivacyPolicy() {
        val uri = "${BuildConfig.APP_URL.trimEnd('/')}/privacidade".toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}
