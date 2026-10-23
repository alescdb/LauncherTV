/*
 * Simple TV Launcher
 * Copyright 2017 Alexandre Del Bigio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cosinus.launchertv;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.File;

import org.cosinus.launchertv.views.ApplicationView;

import org.cosinus.launchertv.activities.Preferences;

import static org.cosinus.launchertv.activities.Preferences.PREFERENCE_DEFAULT_TRANSPARENCY;
import static org.cosinus.launchertv.activities.Preferences.PREFERENCE_TRANSPARENCY;


public class Setup {
	public static final String APPLICATIONS_NAME = "applications";
	private static final String DEFAULTS_INITIALIZED = "defaults_initialized";
	private static final int DEFAULT_GRID_X = 3;
	private static final int DEFAULT_GRID_Y = 2;
	private static final int DEFAULT_MARGIN_X = 5;
	private static final int DEFAULT_MARGIN_Y = 5;

	private final Context mContext;
	private SharedPreferences mPreferences;

	public Setup(Context context) {
		mContext = context;
	}

	public void initializeApplications() {
		// Un ancien accueil vidé volontairement possède encore son fichier de préférences.
		File file = new File(mContext.getApplicationInfo().dataDir, "shared_prefs/" + APPLICATIONS_NAME + ".xml");
		boolean configured = file.exists();
		SharedPreferences preferences = mContext.getSharedPreferences(APPLICATIONS_NAME, Context.MODE_PRIVATE);
		if (preferences.getBoolean(DEFAULTS_INITIALIZED, false))
			return;
		SharedPreferences.Editor editor = preferences.edit();
		if (!configured && preferences.getAll().isEmpty() && getPreferences().getAll().isEmpty()) {
			String[] applications = mContext.getResources().getStringArray(R.array.default_applications);
			int count = Math.min(applications.length, Math.max(2, getGridX()) * Math.max(1, getGridY()));
			for (int position = 0; position < count; position++) {
				String packageName = applications[position].trim();
				if (!TextUtils.isEmpty(packageName))
					editor.putString(ApplicationView.getPreferenceKey(position), packageName);
			}
		}
		editor.putBoolean(DEFAULTS_INITIALIZED, true).commit();
	}

	private SharedPreferences getPreferences() {
		if (mPreferences == null) {
			mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		}
		return (mPreferences);
	}

	private int getInt(String name, int defaultValue) {
		try {
			String value = getPreferences().getString(name, null);
			if (value != null)
				return (Integer.parseInt(value));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isDefaultTransparency() {
		try {
			return (getPreferences().getBoolean(PREFERENCE_DEFAULT_TRANSPARENCY, true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}

	public float getTransparency() {
		float defaultValue = Float.parseFloat(mContext.getString(R.string.transparency_default));
		try {
			return (getPreferences().getFloat(PREFERENCE_TRANSPARENCY, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean showNetwork() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_NETWORK, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showIp() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_ip_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_IP, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean useHomeImage() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_HOME_IMAGE, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean fitHomeImage() {
		try {
			return (!"crop".equals(getPreferences().getString(Preferences.PREFERENCE_IMAGE_SCALE, "fit")));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}

	public boolean showWallpaper() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_wallpaper_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_WALLPAPER, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean keepScreenOn() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SCREEN_ON, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean iconsLocked() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_LOCKED, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showDate() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_date_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_DATE, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean showBattery() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_BATTERY, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showNames() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_NAME, true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}


	public int getGridX() {
		return getInt(Preferences.PREFERENCE_GRID_X, DEFAULT_GRID_X);
	}

	public int getGridY() {
		return getInt(Preferences.PREFERENCE_GRID_Y, DEFAULT_GRID_Y);
	}

	public int getMarginX() {
		return getInt(Preferences.PREFERENCE_MARGIN_X, DEFAULT_MARGIN_X);
	}

	public int getMarginY() {
		return getInt(Preferences.PREFERENCE_MARGIN_Y, DEFAULT_MARGIN_Y);
	}
}
