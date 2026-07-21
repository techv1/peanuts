package com.studiodragon.peanuts.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.studiodragon.peanuts.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
