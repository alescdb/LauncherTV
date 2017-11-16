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

package org.cosinus.launchertv.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;

import java.util.Locale;

@SuppressWarnings("deprecation")
public class Preferences extends PreferenceActivity {
	public static final String PREFERENCE_DEFAULT_TRANSPARENCY = "preference_default_transparency";
	public static final String PREFERENCE_TRANSPARENCY = "preference_transparency";
	public static final String PREFERENCE_SCREEN_ON = "preference_screen_always_on";
	public static final String PREFERENCE_SHOW_DATE = "preference_show_date";
	public static final String PREFERENCE_GRID_X = "preference_grid_x";
	public static final String PREFERENCE_GRID_Y = "preference_grid_y";
	public static final String PREFERENCE_SHOW_NAME = "preference_show_name";
	public static final String PREFERENCE_MARGIN_X = "preference_margin_x";
	public static final String PREFERENCE_MARGIN_Y = "preference_margin_y";
	public static final String PREFERENCE_LOCKED = "preference_locked";
	private static final String PREFERENCE_GOOGLE_PLUS = "preference_google_plus";
	private static final String PREFERENCE_GITHUB = "preference_github";
	private static final String PREFERENCE_ABOUT = "preference_about";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Setup setup = new Setup(this);
		addPreferencesFromResource(R.xml.preferences);

		bindSummary(PREFERENCE_GRID_X, R.string.summary_grid_x);
		bindSummary(PREFERENCE_GRID_Y, R.string.summary_grid_y);
		bindSummary(PREFERENCE_MARGIN_X, R.string.summary_margin_x);
		bindSummary(PREFERENCE_MARGIN_Y, R.string.summary_margin_y);

		findPreference(PREFERENCE_TRANSPARENCY).setEnabled(!setup.isDefaultTransparency());
		findPreference(PREFERENCE_DEFAULT_TRANSPARENCY).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				findPreference(PREFERENCE_TRANSPARENCY).setEnabled(!(boolean) newValue);
				return true;
			}
		});

		findPreference(PREFERENCE_GOOGLE_PLUS).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://plus.google.com/b/104214327962194685169/104214327962194685169/posts")));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getApplicationContext(),
							String.format(getString(R.string.error_opening_link), "Google+", e.getMessage()),
							Toast.LENGTH_LONG).show();
				}
				return (true);
			}
		});
		findPreference(PREFERENCE_GITHUB).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alescdb/LauncherTV")));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getApplicationContext(),
							String.format(getString(R.string.error_opening_link), "Github", e.getMessage()),
							Toast.LENGTH_LONG).show();
				}
				return (true);
			}
		});

		PackageInfo pInfo;
		String version = "#Err";
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = pInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		findPreference(PREFERENCE_ABOUT).setTitle(getString(R.string.app_name) + " version " + version);
		findPreference(PREFERENCE_ABOUT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.cosinus.launchertv")));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getApplicationContext(),
							String.format(getString(R.string.error_opening_link), "Play Store", e.getMessage()),
							Toast.LENGTH_LONG).show();
				}
				return (true);
			}
		});
	}

	private void bindSummary(String key, final int resId) {
		final ListPreference p = (ListPreference) findPreference(key);
		setPreferenceSummaryValue(p, resId, p.getValue());
		p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				setPreferenceSummaryValue(p, resId, (String) newValue);
				return true;
			}
		});
	}

	private void setPreferenceSummaryValue(ListPreference prefs, int resId, String value) {
		prefs.setSummary(
				String.format(Locale.getDefault(), getString(resId), value)
		);
	}

	@Override
	public void onDestroy() {
		if (getParent() == null) {
			setResult(Activity.RESULT_OK, null);
		} else {
			getParent().setResult(Activity.RESULT_OK, null);
		}
		super.onDestroy();
	}
}
