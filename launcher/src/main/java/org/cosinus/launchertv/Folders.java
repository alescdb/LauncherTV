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
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Folders {
	private static final String PREFIX = "folder:";
	private static final String NAME = "name";
	private static final String ITEMS = "items";
	private final Context mContext;
	private final SharedPreferences mPreferences;

	public Folders(Context context) {
		mContext = context.getApplicationContext();
		mPreferences = mContext.getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE);
	}

	public static boolean isFolder(String id) {
		return (id != null && id.startsWith(PREFIX));
	}

	public String create(String name) throws JSONException {
		if (!canEdit() || TextUtils.isEmpty(name.trim()))
			return (null);
		JSONObject record = new JSONObject();
		record.put(NAME, name.trim());
		record.put(ITEMS, new JSONArray());
		String id = PREFIX + UUID.randomUUID();
		return (mPreferences.edit().putString(id, record.toString()).commit() ? id : null);
	}

	public AppInfo get(String id) {
		try {
			JSONObject record = read(id);
			if (record != null)
				return (new AppInfo(id, record.getString(NAME), mContext.getResources().getDrawable(R.drawable.ic_folder)));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return (null);
	}

	public List<AppInfo> load() {
		List<AppInfo> folders = new ArrayList<>();
		for (String id : mPreferences.getAll().keySet()) {
			if (isFolder(id)) {
				AppInfo folder = get(id);
				if (folder != null)
					folders.add(folder);
			}
		}
		return (folders);
	}

	public List<String> getItems(String id) throws JSONException {
		List<String> items = new ArrayList<>();
		JSONObject record = read(id);
		if (record != null) {
			JSONArray values = record.getJSONArray(ITEMS);
			for (int i = 0; i < values.length(); i++)
				items.add(values.getString(i));
		}
		return (items);
	}

	public boolean rename(String id, String name) throws JSONException {
		JSONObject record = read(id);
		if (record == null || TextUtils.isEmpty(name.trim()))
			return (false);
		record.put(NAME, name.trim());
		return (save(id, record));
	}

	public boolean add(String id, String appId) throws JSONException {
		if (TextUtils.isEmpty(appId) || isFolder(appId))
			return (false);
		List<String> items = getItems(id);
		if (items.contains(appId))
			return (true);
		items.add(appId);
		return (setItems(id, items));
	}

	public boolean remove(String id, String appId) throws JSONException {
		List<String> items = getItems(id);
		items.remove(appId);
		return (setItems(id, items));
	}

	public boolean delete(String id) {
		if (!canEdit() || !isFolder(id))
			return (false);
		SharedPreferences.Editor editor = mPreferences.edit().remove(id);
		for (String key : mPreferences.getAll().keySet()) {
			if (key.startsWith("application_") && id.equals(mPreferences.getString(key, null)))
				editor.remove(key);
		}
		return (editor.commit());
	}

	private boolean setItems(String id, List<String> items) throws JSONException {
		JSONObject record = read(id);
		if (record == null)
			return (false);
		record.put(ITEMS, new JSONArray(items));
		return (save(id, record));
	}

	private boolean save(String id, JSONObject record) {
		return (canEdit() && mPreferences.edit().putString(id, record.toString()).commit());
	}

	private boolean canEdit() {
		return (!new Setup(mContext).iconsLocked());
	}

	private JSONObject read(String id) throws JSONException {
		if (!isFolder(id))
			return (null);
		String value = mPreferences.getString(id, null);
		return (value == null ? null : new JSONObject(value));
	}
}
