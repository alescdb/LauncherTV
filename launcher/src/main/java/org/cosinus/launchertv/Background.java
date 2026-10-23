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

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.IOException;

public class Background {
	public static void reset(Context context) throws IOException {
		WallpaperManager manager = WallpaperManager.getInstance(context);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isWallpaperSupported())
			throw new IOException("Wallpaper unsupported");
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			manager.clear();
			return;
		}
		if (!manager.isSetWallpaperAllowed())
			throw new IOException("Wallpaper changes not allowed");
		// Certains anciens Android réinitialisent aussi le verrouillage avec clear(FLAG_SYSTEM).
		Drawable image = manager.getBuiltInDrawable(WallpaperManager.FLAG_SYSTEM);
		if (image == null)
			throw new IOException("Default wallpaper unavailable");
		if (image instanceof BitmapDrawable) {
			setBitmap(manager, ((BitmapDrawable) image).getBitmap());
		} else {
			setImage(manager, image);
		}
	}

	@RequiresApi(Build.VERSION_CODES.N)
	private static void setBitmap(WallpaperManager manager, Bitmap bitmap) throws IOException {
		if (manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM) == 0)
			throw new IOException("Wallpaper not changed");
	}

	@RequiresApi(Build.VERSION_CODES.N)
	private static void setImage(WallpaperManager manager, Drawable image) throws IOException {
		Bitmap bitmap = Bitmap.createBitmap(Math.max(1, image.getIntrinsicWidth()), Math.max(1, image.getIntrinsicHeight()), Bitmap.Config.ARGB_8888);
		try {
			image.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			image.draw(new Canvas(bitmap));
			setBitmap(manager, bitmap);
		} finally {
			bitmap.recycle();
		}
	}
}
