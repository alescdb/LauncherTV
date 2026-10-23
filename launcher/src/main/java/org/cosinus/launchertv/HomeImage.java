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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;

public class HomeImage {
	private static final int MAX_SIZE = 32 * 1024 * 1024;
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

	public void show(boolean visible, boolean fit) {
		mView.setScaleType(fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
		if (!visible || !mFile.isFile()) {
			clear();
			return;
		}
		if (mModified == mFile.lastModified() && (mImage != null || mTask != null))
			return;
		clear();
		mModified = mFile.lastModified();
		mTask = new LoadTask(this, mFile);
		mTask.execute();
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
		File file = File.createTempFile("home_image_", ".tmp", context.getCacheDir());
		boolean valid = false;
		try {
			try (InputStream input = context.getContentResolver().openInputStream(uri);
					FileOutputStream output = new FileOutputStream(file)) {
				if (input == null)
					throw new IOException("Image unavailable");
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

		LoadTask(HomeImage owner, File file) {
			mOwner = new WeakReference<>(owner);
			mFile = file;
		}

		@Override
		protected Bitmap doInBackground(Void... values) {
			try {
				return (read(mFile));
			} catch (IOException | OutOfMemoryError e) {
				e.printStackTrace();
				return (null);
			}
		}

		@Override
		protected void onPostExecute(Bitmap image) {
			HomeImage owner = mOwner.get();
			if (owner == null || owner.mTask != this) {
				onCancelled(image);
				return;
			}
			owner.mTask = null;
			owner.mImage = image;
			if (image != null) {
				owner.mView.setImageBitmap(image);
				owner.mView.setVisibility(View.VISIBLE);
			}
		}

		@Override
		protected void onCancelled(Bitmap image) {
			if (image != null)
				image.recycle();
		}
	}
}
