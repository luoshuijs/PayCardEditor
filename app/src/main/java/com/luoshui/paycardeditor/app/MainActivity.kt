package com.luoshui.paycardeditor.app

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.ModuleStateRepository
import com.luoshui.paycardeditor.feature.home.HomeFragment
import com.luoshui.paycardeditor.feature.preview.CardPreviewFragment
import com.luoshui.paycardeditor.feature.studio.CardStudioFragment
import com.luoshui.paycardeditor.model.CardSnapshotFormatter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.luoshui.paycardeditor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Modern edge-to-edge: lets the system manage status/nav bar colors
        // and icon contrast based on the current theme (M3 day/night).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupWindowInsets()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    showRootFragment(HomeFragment.TAG, HomeFragment())
                    true
                }

                R.id.navigation_studio -> {
                    showRootFragment(CardStudioFragment.TAG, CardStudioFragment())
                    true
                }

                R.id.navigation_preview -> {
                    showRootFragment(CardPreviewFragment.TAG, CardPreviewFragment())
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_home
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                bars.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                bars.bottom
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFragmentContainer) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
    }

    fun showCardSnapshotDialog() {
        val state = ModuleStateRepository.loadHomeState()
        val dialogText = CardSnapshotFormatter.buildDialogText(state)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_card_list_title)
            .setMessage(dialogText)
            .setNeutralButton(R.string.copy_card_info) { _, _ ->
                copyCardSnapshotText(dialogText)
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showMessage(message: CharSequence) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showRootFragment(tag: String, fragment: Fragment) {
        supportActionBar?.title = titleForTag(tag)
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.forEach { 
                if (it.isVisible) {
                    setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED)
                    hide(it)
                }
            }
            if (existing == null) {
                add(R.id.main_fragment_container, fragment, tag)
            } else {
                setMaxLifecycle(existing, androidx.lifecycle.Lifecycle.State.RESUMED)
                show(existing)
            }
        }.commitNow()
    }

    private fun copyCardSnapshotText(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.dialog_card_list_title), text))
        Toast.makeText(this, R.string.copy_card_info_done, Toast.LENGTH_SHORT).show()
    }

    private fun titleForTag(tag: String): String = when (tag) {
        HomeFragment.TAG -> getString(R.string.tab_home)
        CardStudioFragment.TAG -> getString(R.string.tab_studio)
        CardPreviewFragment.TAG -> getString(R.string.tab_preview)
        else -> getString(R.string.app_name)
    }
}
