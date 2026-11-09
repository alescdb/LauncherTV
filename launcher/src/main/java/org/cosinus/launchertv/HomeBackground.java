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

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Fond d'écran d'une activité : l'image du launcher si elle est utilisée,
 * sinon le fond Android, ou du noir quand l'affichage est désactivé.
 */
public class HomeBackground {
	private static final int DIM_COLOR = 0x99000000;
	private final Activity mActivity;
	private final HomeImage mImage;
	private final View mDim;

	public HomeBackground(Activity activity, ImageView view) {
		this(activity, view, null);
	}

	private HomeBackground(Activity activity, ImageView view, View dim) {
		mActivity = activity;
		mImage = new HomeImage(view);
		mDim = dim;
	}

	/**
	 * Ajoute le fond derrière le contenu d'une activité secondaire,
	 * assombri pour que les listes restent lisibles.
	 */
	public static HomeBackground attach(Activity activity) {
		ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
		ImageView image = new ImageView(activity);
		image.setFocusable(false);
		image.setBackgroundColor(0xFF000000);
		image.setVisibility(View.GONE);
		View dim = new View(activity);
		dim.setFocusable(false);
		dim.setBackgroundColor(DIM_COLOR);
		content.addView(image, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		content.addView(dim, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		HomeBackground background = new HomeBackground(activity, image, dim);
		background.update();
		return (background);
	}

	public void update() {
		Setup setup = new Setup(mActivity);
		boolean visible = setup.showWallpaper();
		mImage.show(visible && setup.useHomeImage(), setup.getImageScaleType());
		mActivity.getWindow().setFlags(visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0,
				WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
		mActivity.getWindow().setBackgroundDrawableResource(visible ? android.R.color.transparent : android.R.color.black);
		if (mDim != null)
			mDim.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	public void clear() {
		mImage.clear();
	}
}
