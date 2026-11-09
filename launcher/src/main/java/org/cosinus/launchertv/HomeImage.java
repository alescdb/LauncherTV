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
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

import org.cosinus.launchertv.activities.Preferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;

public class HomeImage {
	private static final int MAX_SIZE = 32 * 1024 * 1024;
	// Image décodée partagée entre l'accueil, la liste des applications et les dossiers.
	private static Bitmap sShared;
	private static long sSharedModified;
	private final ImageView mView;
	private final File mFile;
	private LoadTask mTask;
	private Bitmap mImage;
	private long mModified;

	public HomeImage(ImageView view) {
		mView = view;
		mFile = getFile(view.getContext());
	}

	public static File getFile(Context context) {
		return (new File(context.getFilesDir(), "home_image"));
	}

	public void show(boolean visible, ImageView.ScaleType scaleType) {
		mView.setScaleType(scaleType);
		if (!visible || !mFile.isFile()) {
			clear();
			return;
		}
		long modified = mFile.lastModified();
		if (mModified == modified && (mImage != null || mTask != null))
			return;
		clear();
		mModified = modified;
		Bitmap shared = getShared(modified);
		if (shared != null) {
			display(shared);
			return;
		}
		mTask = new LoadTask(this, mFile, modified);
		mTask.execute();
	}

	private void display(Bitmap image) {
		mImage = image;
		mView.setImageBitmap(image);
		mView.setVisibility(View.VISIBLE);
	}

	private static synchronized Bitmap getShared(long modified) {
		return (sSharedModified == modified ? sShared : null);
	}

	private static synchronized void setShared(Bitmap image, long modified) {
		// L'ancienne image peut encore être affichée ailleurs : le ramasse-miettes la libère.
		sShared = image;
		sSharedModified = modified;
	}

	public void clear() {
		if (mTask != null) {
			mTask.cancel(true);
			mTask = null;
		}
		mView.setVisibility(View.GONE);
		mView.setImageDrawable(null);
		mImage = null;
		mModified = 0;
	}

	public static File copy(Context context, Uri uri) throws IOException {
		InputStream input = context.getContentResolver().openInputStream(uri);
		if (input == null)
			throw new IOException("Image unavailable");
		return (copy(context, input));
	}

	/**
	 * Copie et valide une image dans un fichier temporaire. Le flux est toujours fermé.
	 */
	public static File copy(Context context, InputStream source) throws IOException {
		File file = File.createTempFile("home_image_", ".tmp", context.getCacheDir());
		boolean valid = false;
		try {
			try (InputStream input = source;
					FileOutputStream output = new FileOutputStream(file)) {
				byte[] buffer = new byte[8192];
				int count;
				int size = 0;
				while ((count = input.read(buffer)) != -1) {
					if (Thread.currentThread().isInterrupted())
						throw new InterruptedIOException();
					size += count;
					if (size > MAX_SIZE)
						throw new IOException("Image too large");
					output.write(buffer, 0, count);
				}
			}
			Bitmap image = read(file);
			if (image == null)
				throw new IOException("Invalid image");
			image.recycle();
			valid = true;
			return (file);
		} finally {
			if (!valid)
				file.delete();
		}
	}

	/**
	 * Installe une image validée par {@link #copy} comme fond d'accueil et l'affiche.
	 */
	public static boolean install(Context context, File file) {
		return (install(context, file, true));
	}

	private static boolean install(Context context, File file, boolean show) {
		File target = getFile(context);
		if (file == null || !file.renameTo(target)) {
			if (file != null)
				file.delete();
			return (false);
		}
		target.setLastModified(System.currentTimeMillis());
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean(Preferences.PREFERENCE_HOME_IMAGE, true);
		if (show)
			editor.putBoolean(Preferences.PREFERENCE_SHOW_WALLPAPER, true);
		editor.apply();
		return (true);
	}

	/**
	 * Sans fond Android utilisable (Android TV notamment), l'accueil serait noir :
	 * le fond intégré sert alors de fond par défaut.
	 */
	public static boolean needsBuiltInDefault(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			try {
				if (!WallpaperManager.getInstance(context).isWallpaperSupported())
					return (true);
			} catch (RuntimeException e) {
				return (true);
			}
		}
		// Un téléviseur sans sélecteur ne permet pas de changer son fond Android.
		return (Wallpapers.isTelevision(context)
				&& new Intent(Intent.ACTION_SET_WALLPAPER).resolveActivity(context.getPackageManager()) == null);
	}

	/**
	 * Installe le fond intégré sans modifier l'option « Afficher le fond d'écran ».
	 */
	public static boolean installDefault(Context context) {
		try {
			InputStream input = Wallpapers.openDefault(context);
			return (input != null && install(context, copy(context, input), false));
		} catch (IOException | RuntimeException | OutOfMemoryError e) {
			e.printStackTrace();
			return (false);
		}
	}

	/**
	 * Retire l'image d'accueil privée. L'image Android reste inchangée.
	 */
	public static boolean remove(Context context) {
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean(Preferences.PREFERENCE_HOME_IMAGE, false).apply();
		File file = getFile(context);
		return (!file.exists() || file.delete());
	}

	private static Bitmap read(File file) throws IOException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getPath(), options);
		if (options.outWidth <= 0 || options.outHeight <= 0)
			throw new IOException("Invalid image dimensions");
		// Limiter aussi la mémoire sur les anciennes box à petit tas Java.
		long pixels = Math.min(2048L * 2048L, Runtime.getRuntime().maxMemory() / 32L);
		options.inSampleSize = 1;
		while ((long) options.outWidth / options.inSampleSize * (options.outHeight / options.inSampleSize) > pixels)
			options.inSampleSize *= 2;
		options.inJustDecodeBounds = false;
		Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
		if (image == null)
			throw new IOException("Cannot decode image");
		Matrix matrix = getOrientation(file);
		if (matrix.isIdentity())
			return (image);
		try {
			return (Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true));
		} finally {
			image.recycle();
		}
	}

	private static Matrix getOrientation(File file) {
		Matrix matrix = new Matrix();
		try {
			int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
			switch (orientation) {
				case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
				case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
				case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
				case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
				case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
				case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
				case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
				default: break;
			}
		} catch (IOException e) {
			// Les formats sans EXIF gardent leur orientation native.
		}
		return (matrix);
	}

	private static class LoadTask extends AsyncTask<Void, Void, Bitmap> {
		private final WeakReference<HomeImage> mOwner;
		private final File mFile;
		private final long mModified;

		LoadTask(HomeImage owner, File file, long modified) {
			mOwner = new WeakReference<>(owner);
			mFile = file;
			mModified = modified;
		}

		@Override
		protected Bitmap doInBackground(Void... values) {
			Bitmap shared = getShared(mModified);
			if (shared != null)
				return (shared);
			try {
				// Libérer l'ancienne image partagée avant d'en décoder une nouvelle.
				setShared(null, 0);
				Bitmap image = read(mFile);
				setShared(image, mModified);
				return (image);
			} catch (IOException | OutOfMemoryError e) {
				e.printStackTrace();
				return (null);
			}
		}

		@Override
		protected void onPostExecute(Bitmap image) {
			HomeImage owner = mOwner.get();
			if (owner == null || owner.mTask != this)
				return;
			owner.mTask = null;
			if (image != null)
				owner.display(image);
		}
	}
}
