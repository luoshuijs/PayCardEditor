package com.luoshui.paycardeditor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.luoshui.paycardeditor.databinding.ActivityTroubleshootBinding

class TroubleshootActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTroubleshootBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTroubleshootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.troubleshoot_title)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.troubleshoot_fragment_container, TroubleshootFragment())
            }
        }
    }
}
