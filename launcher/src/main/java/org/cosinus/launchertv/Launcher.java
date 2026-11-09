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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.cosinus.launchertv.fragments.ApplicationFragment;

public class Launcher extends FragmentActivity {
	private static final String PREFERENCE_DEFAULT_CHECKED = "default_wallpaper_checked";
	private HomeBackground mBackground;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		new Setup(this).initializeApplications();

		setFullScreen();
		setContentView(R.layout.activity_launcher);
		mBackground = new HomeBackground(this, (ImageView) findViewById(R.id.home_image));
		mBackground.update();
		installDefaultWallpaper();

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.container, ApplicationFragment.newInstance(), ApplicationFragment.TAG)
					.commit();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		setFullScreen();
		mBackground.update();
	}

	@Override
	protected void onDestroy() {
		mBackground.clear();
		super.onDestroy();
	}

	/**
	 * Applique le fond intégré une seule fois, après une nouvelle installation.
	 * Une mise à jour conserve le fond affiché jusque-là.
	 */
	private void installDefaultWallpaper() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (preferences.getBoolean(PREFERENCE_DEFAULT_CHECKED, false))
			return;
		preferences.edit().putBoolean(PREFERENCE_DEFAULT_CHECKED, true).apply();
		if (!isNewInstall() || HomeImage.getFile(this).isFile() || !HomeImage.needsBuiltInDefault(this))
			return;
		final Context context = getApplicationContext();
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (!HomeImage.installDefault(context))
					return;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!isFinishing())
							mBackground.update();
					}
				});
			}
		}, "default-wallpaper").start();
	}

	private boolean isNewInstall() {
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			return (info.firstInstallTime == info.lastUpdateTime);
		} catch (PackageManager.NameNotFoundException e) {
			return (false);
		}
	}

	private void setFullScreen() {
		try {
			if (Build.VERSION.SDK_INT < 19) {
				getWindow().setFlags(
						WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN
				);
			} else {
				View decorView = getWindow().getDecorView();

				int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
						| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
						| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
						| View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
						| View.SYSTEM_UI_FLAG_IMMERSIVE;

				decorView.setSystemUiVisibility(uiOptions);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
