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

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.util.Size;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sources d'images pour le sélecteur de fond d'écran intégré :
 * images fournies avec l'application et images locales (stockage interne et clés USB).
 */
public final class Wallpapers {
	public static final int KIND_ASSET = 0;
	public static final int KIND_MEDIA = 1;
	public static final int KIND_PERMISSION = 2;
	public static final int KIND_BROWSE = 3;
	public static final int KIND_SYSTEM = 4;

	private static final String ASSET_DIR = "wallpapers";
	private static final int MAX_MEDIA = 500;

	private Wallpapers() {
	}

	public static final class Item {
		public final int kind;
		public final String name;
		final String mAsset;
		final Uri mUri;
		final long mId;
		final int mOrientation;

		Item(int kind, String name, String asset, Uri uri, long id, int orientation) {
			this.kind = kind;
			this.name = name;
			mAsset = asset;
			mUri = uri;
			mId = id;
			mOrientation = orientation;
		}

		public static Item action(int kind, String name) {
			return (new Item(kind, name, null, null, 0, 0));
		}

		public boolean isImage() {
			return (kind == KIND_ASSET || kind == KIND_MEDIA);
		}

		public String getKey() {
			return (kind == KIND_ASSET ? "asset:" + mAsset : "media:" + mUri);
		}

		public InputStream open(Context context) throws IOException {
			if (kind == KIND_ASSET)
				return (context.getAssets().open(ASSET_DIR + "/" + mAsset));
			if (kind == KIND_MEDIA) {
				InputStream input = context.getContentResolver().openInputStream(mUri);
				if (input == null)
					throw new IOException("Image unavailable");
				return (input);
			}
			throw new IOException("Not an image");
		}
	}

	public static List<Item> getBuiltIn(Context context, String name) {
		List<Item> items = new ArrayList<>();
		try {
			String[] files = context.getAssets().list(ASSET_DIR);
			if (files != null) {
				Arrays.sort(files);
				for (String file : files)
					items.add(new Item(KIND_ASSET, name, file, null, 0, 0));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (items);
	}

	/**
	 * Ouvre le premier fond intégré, ou null s'il n'y en a aucun.
	 */
	public static InputStream openDefault(Context context) throws IOException {
		String[] files = context.getAssets().list(ASSET_DIR);
		if (files == null || files.length == 0)
			return (null);
		Arrays.sort(files);
		return (context.getAssets().open(ASSET_DIR + "/" + files[0]));
	}

	public static boolean isTelevision(Context context) {
		PackageManager manager = context.getPackageManager();
		return (manager.hasSystemFeature("android.software.leanback")
				|| manager.hasSystemFeature("android.hardware.type.television"));
	}

	public static String getPermission() {
		if (Build.VERSION.SDK_INT >= 33)
			return (Manifest.permission.READ_MEDIA_IMAGES);
		return ("android.permission.READ_EXTERNAL_STORAGE");
	}

	public static boolean canReadMedia(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
			return (true);
		if (context.checkSelfPermission(getPermission()) == PackageManager.PERMISSION_GRANTED)
			return (true);
		// Accès partiel accordé par Android 14+ : les images choisies restent lisibles.
		return (Build.VERSION.SDK_INT >= 34
				&& context.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED);
	}

	/**
	 * Liste les images indexées par Android. Depuis Android 10, le volume « external »
	 * regroupe le stockage interne et les supports amovibles (clés USB, cartes SD).
	 */
	public static List<Item> getMedia(Context context) {
		List<Item> items = new ArrayList<>();
		Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		String[] projection = {
				MediaStore.Images.Media._ID,
				MediaStore.Images.Media.DISPLAY_NAME,
				MediaStore.Images.Media.ORIENTATION
		};
		try (Cursor cursor = context.getContentResolver().query(base, projection, null, null,
				MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
			if (cursor == null)
				return (items);
			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION);
			while (cursor.moveToNext() && items.size() < MAX_MEDIA) {
				long id = cursor.getLong(idColumn);
				items.add(new Item(KIND_MEDIA, cursor.getString(nameColumn), null,
						ContentUris.withAppendedId(base, id), id, cursor.getInt(orientationColumn)));
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		return (items);
	}

	/**
	 * Décode une image réduite pour l'affichage (vignette ou aperçu).
	 */
	public static Bitmap decode(Context context, Item item, int width, int height) throws IOException {
		if (item.kind == KIND_MEDIA) {
			Bitmap thumbnail = loadThumbnail(context, item, width, height);
			if (thumbnail != null)
				return (thumbnail);
		}
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try (InputStream input = item.open(context)) {
			BitmapFactory.decodeStream(input, null, options);
		}
		if (options.outWidth <= 0 || options.outHeight <= 0)
			throw new IOException("Invalid image");
		int sample = 1;
		while (options.outWidth / (sample * 2) >= width && options.outHeight / (sample * 2) >= height)
			sample *= 2;
		options = new BitmapFactory.Options();
		options.inSampleSize = sample;
		options.inPreferredConfig = Bitmap.Config.RGB_565;
		Bitmap image;
		try (InputStream input = item.open(context)) {
			image = BitmapFactory.decodeStream(input, null, options);
		}
		if (image == null)
			throw new IOException("Cannot decode image");
		return (rotate(image, item.mOrientation));
	}

	private static Bitmap loadThumbnail(Context context, Item item, int width, int height) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
				return (loadThumbnailQ(context.getContentResolver(), item.mUri, width, height));
			// Les miniatures système ne suffisent que pour la grille.
			if (width > 512 || height > 512)
				return (null);
			Bitmap image = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), item.mId,
					MediaStore.Images.Thumbnails.MINI_KIND, null);
			return (image == null ? null : rotate(image, item.mOrientation));
		} catch (Exception e) {
			return (null);
		}
	}

	@RequiresApi(Build.VERSION_CODES.Q)
	private static Bitmap loadThumbnailQ(ContentResolver resolver, Uri uri, int width, int height) throws IOException {
		return (resolver.loadThumbnail(uri, new Size(width, height), null));
	}

	private static Bitmap rotate(Bitmap image, int orientation) {
		if (orientation % 360 == 0)
			return (image);
		Matrix matrix = new Matrix();
		matrix.setRotate(orientation);
		try {
			return (Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true));
		} finally {
			image.recycle();
		}
	}
}
