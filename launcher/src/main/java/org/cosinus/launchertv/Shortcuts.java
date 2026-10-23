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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import org.cosinus.launchertv.views.ApplicationView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Shortcuts {
	private static final String PREFIX = "shortcut:";
	private static final String PENDING_PREFIX = "pending_shortcut:";
	private static final String NAME = "name";
	private static final String ICON = "icon";
	private static final String URI = "uri";
	private static final String ACTION = "action";
	private static final String PACKAGE = "package";
	private static final String SHORTCUT = "shortcut";
	private static final String USER = "user";
	private static final int ICON_SIZE = 256;

	private final Context mContext;
	private final SharedPreferences mPreferences;

	public Shortcuts(Context context) {
		mContext = context.getApplicationContext();
		mPreferences = mContext.getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE);
	}

	public static boolean isShortcut(String id) {
		return (id != null && id.startsWith(PREFIX));
	}

	public static boolean isPending(String id) {
		return (id != null && id.startsWith(PENDING_PREFIX));
	}

	public void request(Intent data) throws JSONException, PackageManager.NameNotFoundException {
		Intent intent = (Intent) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
		String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
		if (intent == null || TextUtils.isEmpty(name))
			throw new IllegalArgumentException("Invalid shortcut");
		JSONObject record = createRecord(name, getIcon(data));
		record.put(URI, intent.toUri(Intent.URI_INTENT_SCHEME));
		record.put(ACTION, intent.getAction());
		mPreferences.edit().putString(PENDING_PREFIX + UUID.randomUUID(), record.toString()).apply();
	}

	public String getPending() {
		for (String key : mPreferences.getAll().keySet()) {
			if (isPending(key))
				return (key);
		}
		return (null);
	}

	public AppInfo get(String id) {
		try {
			JSONObject record = read(id);
			if (record != null)
				return (new AppInfo(id, record.getString(NAME), decodeIcon(record.getString(ICON))));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (null);
	}

	public List<AppInfo> load() {
		List<AppInfo> apps = new ArrayList<>();
		for (String key : mPreferences.getAll().keySet()) {
			if (isShortcut(key)) {
				AppInfo app = get(key);
				if (app != null)
					apps.add(app);
			}
		}
		return (apps);
	}

	public int findEmpty() {
		Setup setup = new Setup(mContext);
		int count = Math.max(2, setup.getGridX()) * Math.max(1, setup.getGridY());
		for (int position = 0; position < count; position++) {
			if (TextUtils.isEmpty(mPreferences.getString(ApplicationView.getPreferenceKey(position), null)))
				return (position);
		}
		return (-1);
	}

	public boolean accept(String id) throws JSONException {
		if (!isPending(id))
			return (false);
		JSONObject record = read(id);
		return (record != null && save(id, record));
	}

	public boolean addPinned(String name, Drawable icon, String packageName, String shortcutId, long user) throws JSONException {
		JSONObject record = createRecord(name, icon);
		record.put(PACKAGE, packageName);
		record.put(SHORTCUT, shortcutId);
		record.put(USER, user);
		return (save(null, record));
	}

	public void discard(String id) {
		if (isPending(id))
			mPreferences.edit().remove(id).apply();
	}

	public String getPackage(String id) {
		if (!isShortcut(id))
			return (null);
		try {
			JSONObject record = read(id);
			if (record == null)
				return (null);
			if (record.has(PACKAGE))
				return (record.getString(PACKAGE));
			Intent intent = Intent.parseUri(record.getString(URI), Intent.URI_INTENT_SCHEME);
			if (intent.getComponent() != null)
				return (intent.getComponent().getPackageName());
			if (intent.getPackage() != null)
				return (intent.getPackage());
			ResolveInfo target = mContext.getPackageManager().resolveActivity(intent, 0);
			return (target == null ? null : target.activityInfo.packageName);
		} catch (Exception e) {
			return (null);
		}
	}

	public void launch(String id) throws Exception {
		JSONObject record = read(id);
		if (record == null)
			throw new ActivityNotFoundException();
		if (record.has(URI)) {
			Intent intent = Intent.parseUri(record.getString(URI), Intent.URI_INTENT_SCHEME);
			intent.setAction(record.optString(ACTION, null));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			PinShortcut.launch(mContext, record.getString(PACKAGE), record.getString(SHORTCUT), record.getLong(USER));
		} else {
			throw new ActivityNotFoundException();
		}
	}

	private boolean save(String pending, JSONObject record) {
		int position = findEmpty();
		if (new Setup(mContext).iconsLocked() || position < 0)
			return (false);
		String id = PREFIX + UUID.randomUUID();
		SharedPreferences.Editor editor = mPreferences.edit();
		editor.putString(id, record.toString());
		editor.putString(ApplicationView.getPreferenceKey(position), id);
		if (pending != null)
			editor.remove(pending);
		return (editor.commit());
	}

	private JSONObject read(String id) throws JSONException {
		String value = mPreferences.getString(id, null);
		return (value == null ? null : new JSONObject(value));
	}

	private JSONObject createRecord(String name, Drawable icon) throws JSONException {
		JSONObject record = new JSONObject();
		record.put(NAME, name);
		record.put(ICON, encodeIcon(icon));
		return (record);
	}

	private Drawable getIcon(Intent data) throws PackageManager.NameNotFoundException {
		Bitmap bitmap = (Bitmap) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
		if (bitmap != null)
			return (new BitmapDrawable(mContext.getResources(), bitmap));
		Intent.ShortcutIconResource icon = (Intent.ShortcutIconResource) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
		if (icon != null) {
			Resources resources = mContext.getPackageManager().getResourcesForApplication(icon.packageName);
			int id = resources.getIdentifier(icon.resourceName, null, null);
			if (id != 0)
				return (resources.getDrawable(id));
		}
		return (mContext.getPackageManager().getDefaultActivityIcon());
	}

	private String encodeIcon(Drawable icon) {
		Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
		Rect bounds = new Rect(icon.getBounds());
		icon.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
		icon.draw(new Canvas(bitmap));
		icon.setBounds(bounds);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
		return (Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
	}

	private Drawable decodeIcon(String value) {
		byte[] bytes = Base64.decode(value, Base64.NO_WRAP);
		Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		return (bitmap == null ? mContext.getPackageManager().getDefaultActivityIcon() : new BitmapDrawable(mContext.getResources(), bitmap));
	}
}
