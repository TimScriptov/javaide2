/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.duy.ide.javaide.setting;

import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import com.duy.ide.R;

public class CompilerSettingFragment extends PreferenceFragmentCompat {
    private ListPreference key_pref_source_compatibility;
    private ListPreference key_pref_target_compatibility;
    private EditTextPreference key_pref_source_encoding;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preference_compiler);
        key_pref_source_compatibility = findPreference("key_pref_source_compatibility");
        key_pref_target_compatibility = findPreference("key_pref_target_compatibility");
        key_pref_source_encoding = findPreference("key_pref_source_encoding");
    }
}
