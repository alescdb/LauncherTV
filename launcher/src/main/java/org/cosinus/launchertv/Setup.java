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

import org.cosinus.launchertv.activities.Preferences;

import static org.cosinus.launchertv.activities.Preferences.PREFERENCE_DEFAULT_TRANSPARENCY;
import static org.cosinus.launchertv.activities.Preferences.PREFERENCE_TRANSPARENCY;


public class Setup {
	private static final int DEFAULT_GRID_X = 3;
	private static final int DEFAULT_GRID_Y = 2;
	private static final int DEFAULT_MARGIN_X = 5;
	private static final int DEFAULT_MARGIN_Y = 5;

	private final Context mContext;
	private SharedPreferences mPreferences;

	public Setup(Context context) {
		mContext = context;
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
		try {
			return (getPreferences().getFloat(PREFERENCE_TRANSPARENCY, 0.5F));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (0.5F);
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
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_DATE, true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
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
