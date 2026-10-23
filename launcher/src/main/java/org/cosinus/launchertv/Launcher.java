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

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.cosinus.launchertv.fragments.ApplicationFragment;

public class Launcher extends FragmentActivity {
	private HomeImage mHomeImage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		new Setup(this).initializeApplications();

		setFullScreen();
		setContentView(R.layout.activity_launcher);
		mHomeImage = new HomeImage((ImageView) findViewById(R.id.home_image));
		setWallpaper();

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
		setWallpaper();
	}

	@Override
	protected void onDestroy() {
		mHomeImage.clear();
		super.onDestroy();
	}

	private void setWallpaper() {
		Setup setup = new Setup(this);
		boolean visible = setup.showWallpaper();
		mHomeImage.show(visible && setup.useHomeImage(), setup.fitHomeImage());
		getWindow().setFlags(visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0,
				WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
		getWindow().setBackgroundDrawableResource(visible ? android.R.color.transparent : android.R.color.black);
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
