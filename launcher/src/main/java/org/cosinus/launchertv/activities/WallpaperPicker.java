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
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.cosinus.launchertv.HomeImage;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Wallpapers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sélecteur de fond d'écran utilisable à la télécommande. Il remplace le sélecteur
 * Android, absent sur la plupart des appareils Android TV.
 */
public class WallpaperPicker extends Activity {
	private static final int REQUEST_PERMISSION = 1;
	private static final int REQUEST_BROWSE = 2;
	private static final int THUMB_WIDTH = 320;
	private static final int THUMB_HEIGHT = 180;
	private static final long PREVIEW_DELAY = 200;
	private static final String PREFERENCE_PERMISSION_ASKED = "wallpaper_permission_asked";

	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private final List<Wallpapers.Item> mItems = new ArrayList<>();
	private ExecutorService mThumbnails;
	private ExecutorService mWorker;
	private ExecutorService mPreviews;
	private LruCache<String, Bitmap> mCache;
	private List<Wallpapers.Item> mBuiltIn;
	private List<Wallpapers.Item> mMedia;
	private ItemAdapter mAdapter;
	private GridView mGrid;
	private ImageView mPreview;
	private TextView mHint;
	private View mProgress;
	private boolean mCanReadMedia;
	private boolean mLoadingMedia;
	private boolean mApplying;
	private boolean mDestroyed;
	private volatile int mPreviewGeneration;
	private Runnable mPreviewTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wallpaper_picker);
		mGrid = (GridView) findViewById(R.id.wallpaper_grid);
		mPreview = (ImageView) findViewById(R.id.wallpaper_preview);
		mHint = (TextView) findViewById(R.id.wallpaper_hint);
		mProgress = findViewById(R.id.wallpaper_progress);

		mThumbnails = Executors.newFixedThreadPool(3);
		mWorker = Executors.newSingleThreadExecutor();
		mPreviews = Executors.newSingleThreadExecutor();
		int cacheSize = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8);
		mCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return (value.getRowBytes() * value.getHeight());
			}
		};
		mBuiltIn = Wallpapers.getBuiltIn(this, getString(R.string.wallpaper_builtin));

		mAdapter = new ItemAdapter();
		mGrid.setAdapter(mAdapter);
		mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				onItemChosen(mItems.get(position));
			}
		});
		mGrid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				schedulePreview(mItems.get(position));
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mCanReadMedia = Wallpapers.canReadMedia(this);
		refreshItems();
		if (!mItems.isEmpty())
			schedulePreview(mItems.get(0));
		mGrid.requestFocus();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// La permission a pu être accordée depuis les paramètres Android.
		boolean canRead = Wallpapers.canReadMedia(this);
		if (canRead != mCanReadMedia) {
			mCanReadMedia = canRead;
			mMedia = null;
			refreshItems();
		}
	}

	@Override
	protected void onDestroy() {
		mDestroyed = true;
		mHandler.removeCallbacksAndMessages(null);
		mThumbnails.shutdownNow();
		mPreviews.shutdownNow();
		// Une application déjà lancée se termine même si l'écran est fermé.
		mWorker.shutdown();
		mCache.evictAll();
		super.onDestroy();
	}

	private void refreshItems() {
		mItems.clear();
		mItems.addAll(mBuiltIn);
		if (!mCanReadMedia)
			mItems.add(Wallpapers.Item.action(Wallpapers.KIND_PERMISSION, getString(R.string.wallpaper_local_permission)));
		if (getBrowseIntent().resolveActivity(getPackageManager()) != null)
			mItems.add(Wallpapers.Item.action(Wallpapers.KIND_BROWSE, getString(R.string.wallpaper_browse)));
		if (isSystemPickerAvailable())
			mItems.add(Wallpapers.Item.action(Wallpapers.KIND_SYSTEM, getString(R.string.wallpaper_system)));
		if (mCanReadMedia && mMedia != null)
			mItems.addAll(mMedia);
		mAdapter.notifyDataSetChanged();

		if (!mCanReadMedia) {
			mHint.setText(R.string.wallpaper_hint);
		} else if (mMedia == null) {
			mHint.setText(R.string.wallpaper_loading);
			loadMedia();
		} else if (mMedia.isEmpty()) {
			mHint.setText(R.string.wallpaper_none);
		} else {
			mHint.setText(getResources().getQuantityString(R.plurals.wallpaper_count, mMedia.size(), mMedia.size()));
		}
	}

	private void loadMedia() {
		if (mLoadingMedia)
			return;
		mLoadingMedia = true;
		final Context context = getApplicationContext();
		mWorker.execute(new Runnable() {
			@Override
			public void run() {
				final List<Wallpapers.Item> media = Wallpapers.getMedia(context);
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mLoadingMedia = false;
						if (mDestroyed || !mCanReadMedia)
							return;
						mMedia = media;
						refreshItems();
					}
				});
			}
		});
	}

	private void onItemChosen(Wallpapers.Item item) {
		if (mApplying)
			return;
		switch (item.kind) {
			case Wallpapers.KIND_ASSET:
			case Wallpapers.KIND_MEDIA:
				apply(item, null);
				break;
			case Wallpapers.KIND_PERMISSION:
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
					requestMediaPermission();
				break;
			case Wallpapers.KIND_BROWSE:
				try {
					startActivityForResult(getBrowseIntent(), REQUEST_BROWSE);
				} catch (ActivityNotFoundException | SecurityException e) {
					Toast.makeText(this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show();
				}
				break;
			case Wallpapers.KIND_SYSTEM:
				openSystemPicker();
				break;
			default:
				break;
		}
	}

	private void apply(final Wallpapers.Item item, final Uri uri) {
		mApplying = true;
		mProgress.setVisibility(View.VISIBLE);
		final Context context = getApplicationContext();
		mWorker.execute(new Runnable() {
			@Override
			public void run() {
				boolean success;
				try {
					File file = uri != null ? HomeImage.copy(context, uri) : HomeImage.copy(context, item.open(context));
					success = HomeImage.install(context, file);
				} catch (IOException | RuntimeException | OutOfMemoryError e) {
					e.printStackTrace();
					success = false;
				}
				final boolean result = success;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(context, result ? R.string.image_imported : R.string.image_error, Toast.LENGTH_LONG).show();
						if (mDestroyed)
							return;
						mApplying = false;
						mProgress.setVisibility(View.GONE);
						if (result) {
							setResult(RESULT_OK);
							finish();
						}
					}
				});
			}
		});
	}

	private static Intent getBrowseIntent() {
		return (new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE));
	}

	private boolean isSystemPickerAvailable() {
		if (new Intent(Intent.ACTION_SET_WALLPAPER).resolveActivity(getPackageManager()) == null)
			return (false);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
			return (true);
		try {
			return (WallpaperManager.getInstance(this).isWallpaperSupported());
		} catch (RuntimeException e) {
			return (false);
		}
	}

	private void openSystemPicker() {
		try {
			startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
			// Afficher le fond Android choisi au lieu de l'image du launcher.
			PreferenceManager.getDefaultSharedPreferences(this).edit()
					.putBoolean(Preferences.PREFERENCE_HOME_IMAGE, false)
					.putBoolean(Preferences.PREFERENCE_SHOW_WALLPAPER, true)
					.apply();
			finish();
		} catch (ActivityNotFoundException | SecurityException e) {
			Toast.makeText(this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show();
		}
	}

	@RequiresApi(Build.VERSION_CODES.M)
	private void requestMediaPermission() {
		String permission = Wallpapers.getPermission();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Après un refus définitif, Android n'affiche plus la demande : ouvrir les paramètres.
		if (preferences.getBoolean(PREFERENCE_PERMISSION_ASKED, false) && !shouldShowRequestPermissionRationale(permission)) {
			Toast.makeText(this, R.string.wallpaper_permission_denied, Toast.LENGTH_LONG).show();
			try {
				startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
			} catch (ActivityNotFoundException | SecurityException e) {
				e.printStackTrace();
			}
			return;
		}
		preferences.edit().putBoolean(PREFERENCE_PERMISSION_ASKED, true).apply();
		requestPermissions(new String[]{permission}, REQUEST_PERMISSION);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != REQUEST_PERMISSION)
			return;
		mCanReadMedia = Wallpapers.canReadMedia(this);
		if (mCanReadMedia) {
			mMedia = null;
			refreshItems();
		} else if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, R.string.wallpaper_permission_denied, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == REQUEST_BROWSE && resultCode == RESULT_OK && intent != null && intent.getData() != null && !mApplying)
			apply(null, intent.getData());
	}

	private void schedulePreview(final Wallpapers.Item item) {
		if (!item.isImage())
			return;
		if (mPreviewTask != null)
			mHandler.removeCallbacks(mPreviewTask);
		final int generation = ++mPreviewGeneration;
		Bitmap cached = mCache.get(item.getKey());
		if (cached != null)
			mPreview.setImageBitmap(cached);
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		final int width = Math.max(960, metrics.widthPixels / 2);
		final int height = Math.max(540, metrics.heightPixels / 2);
		final Context context = getApplicationContext();
		mPreviewTask = new Runnable() {
			@Override
			public void run() {
				mPreviews.execute(new Runnable() {
					@Override
					public void run() {
						if (generation != mPreviewGeneration)
							return;
						Bitmap image = null;
						try {
							image = Wallpapers.decode(context, item, width, height);
						} catch (IOException | RuntimeException | OutOfMemoryError e) {
							e.printStackTrace();
						}
						final Bitmap result = image;
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								if (result != null && !mDestroyed && generation == mPreviewGeneration)
									mPreview.setImageBitmap(result);
							}
						});
					}
				});
			}
		};
		mHandler.postDelayed(mPreviewTask, PREVIEW_DELAY);
	}

	private void loadThumbnail(final Wallpapers.Item item, final ViewHolder holder) {
		final String key = item.getKey();
		final Context context = getApplicationContext();
		mThumbnails.execute(new Runnable() {
			@Override
			public void run() {
				// Ignorer les vignettes déjà sorties de l'écran.
				if (!key.equals(holder.mKey))
					return;
				Bitmap image = mCache.get(key);
				if (image == null) {
					try {
						image = Wallpapers.decode(context, item, THUMB_WIDTH, THUMB_HEIGHT);
					} catch (IOException | RuntimeException | OutOfMemoryError e) {
						e.printStackTrace();
					}
				}
				final Bitmap result = image;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mDestroyed || result == null)
							return;
						mCache.put(key, result);
						if (key.equals(holder.mKey))
							holder.mImage.setImageBitmap(result);
					}
				});
			}
		});
	}

	private static final class ViewHolder {
		final ImageView mImage;
		final TextView mName;
		volatile String mKey;

		ViewHolder(View view) {
			mImage = (ImageView) view.findViewById(R.id.wallpaper_image);
			mName = (TextView) view.findViewById(R.id.wallpaper_name);
		}
	}

	private final class ItemAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return (mItems.size());
		}

		@Override
		public Object getItem(int position) {
			return (mItems.get(position));
		}

		@Override
		public long getItemId(int position) {
			return (position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wallpaper_item, parent, false);
				view.setTag(new ViewHolder(view));
			}
			ViewHolder holder = (ViewHolder) view.getTag();
			Wallpapers.Item item = mItems.get(position);
			holder.mName.setText(item.name);
			if (!item.isImage()) {
				holder.mKey = null;
				holder.mImage.setScaleType(ImageView.ScaleType.CENTER);
				holder.mImage.setImageResource(getIcon(item.kind));
				return (view);
			}
			holder.mImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
			holder.mKey = item.getKey();
			Bitmap cached = mCache.get(holder.mKey);
			holder.mImage.setImageBitmap(cached);
			if (cached == null)
				loadThumbnail(item, holder);
			return (view);
		}

		private int getIcon(int kind) {
			switch (kind) {
				case Wallpapers.KIND_PERMISSION:
					return (R.drawable.ic_folder);
				case Wallpapers.KIND_BROWSE:
					return (R.drawable.ic_apps);
				default:
					return (R.drawable.ic_tune);
			}
		}
	}
}
