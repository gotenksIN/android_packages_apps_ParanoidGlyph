/*
 * Copyright (C) 2022-2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.glyph.manager;

import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.android.internal.util.ArrayUtils;

import java.util.HashSet;
import java.util.Set;

import co.aospa.glyph.utils.Constants;
import co.aospa.glyph.utils.FileUtils;
import co.aospa.glyph.utils.ResourceUtils;

public final class SettingsManager {

    private static final String TAG = "GlyphSettingsManager";
    private static final boolean DEBUG = true;
    private static final String SECURE_SETTINGS_MIGRATED = "secure_settings_migrated";

    private static synchronized SharedPreferences getPreferences() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(Constants.CONTEXT);
        if (!preferences.getBoolean(SECURE_SETTINGS_MIGRATED, false)) {
            SharedPreferences.Editor editor = preferences.edit();
            migrateSecureBoolean(editor, Constants.GLYPH_ENABLE, true);
            migrateSecureBoolean(editor, Constants.GLYPH_CALL_ENABLE, true);
            migrateSecureBoolean(editor, Constants.GLYPH_NOTIFS_ENABLE, true);
            editor.putBoolean(SECURE_SETTINGS_MIGRATED, true).commit();
        }
        return preferences;
    }

    private static void migrateSecureBoolean(SharedPreferences.Editor editor, String key,
            boolean defaultValue) {
        boolean value = Settings.Secure.getInt(Constants.CONTEXT.getContentResolver(), key,
                defaultValue ? 1 : 0) != 0;
        editor.putBoolean(key, value);
    }

    public static boolean enableGlyph(boolean enable) {
        return getPreferences().edit().putBoolean(Constants.GLYPH_ENABLE, enable).commit();
    }

    public static boolean isGlyphEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_ENABLE, true);
    }

    public static boolean isGlyphFlipEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_FLIP_ENABLE, true) && isGlyphEnabled();
    }

    public static int getGlyphBrightness() {
        int[] levels = Constants.getBrightnessLevels();
        int brightnessSetting = getGlyphBrightnessSetting();
        return levels[brightnessSetting - 1];
    }

    public static int getGlyphBrightnessSetting() {
        int d = 3; if (FileUtils.readLine("/mnt/vendor/persist/color") == "white") d = 2;
        return getPreferences().getInt(Constants.GLYPH_BRIGHTNESS, d);
    }

    public static boolean isGlyphChargingEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_CHARGING_LEVEL_ENABLE, true)
                && isGlyphEnabled();
    }

    public static boolean isGlyphPowershareEnabled() {
        return Constants.isPowershareSupported()
                && getPreferences().getBoolean(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE, true)
                && isGlyphEnabled();
    }

    public static boolean isGlyphCallEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_CALL_ENABLE, true) && isGlyphEnabled();
    }

    public static boolean setGlyphCallEnabled(boolean enable) {
        return getPreferences().edit().putBoolean(Constants.GLYPH_CALL_ENABLE, enable).commit();
    }

    public static String getGlyphCallAnimation() {
        return getPreferences().getString(Constants.GLYPH_CALL_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static boolean isGlyphMusicVisualizerEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE, false)
                && isGlyphEnabled();
    }

    public static boolean isGlyphVolumeLevelEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_VOLUME_LEVEL_ENABLE, true)
                && isGlyphEnabled();
    }

    public static boolean isGlyphNotifsEnabled() {
        return getPreferences().getBoolean(Constants.GLYPH_NOTIFS_ENABLE, true) && isGlyphEnabled();
    }

    public static boolean setGlyphNotifsEnabled(boolean enable) {
        return getPreferences().edit().putBoolean(Constants.GLYPH_NOTIFS_ENABLE, enable).commit();
    }

    public static String getGlyphNotifsAnimation() {
        return getPreferences().getString(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static boolean isGlyphNotifsAppEnabled(String app) {
        return getPreferences().getBoolean(app, true) && isGlyphNotifsEnabled();
    }

    public static boolean isGlyphNotifsAppEssential(String app) {
        Set<String> selectedValues = getPreferences()
                .getStringSet(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL, new HashSet<String>());
        return selectedValues.contains(app) && isGlyphNotifsEnabled();
    }
}
