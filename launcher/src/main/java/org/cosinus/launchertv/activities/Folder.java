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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.Folders;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.Setup;
import org.cosinus.launchertv.Shortcuts;
import org.cosinus.launchertv.Utils;
import org.cosinus.launchertv.views.ApplicationAdapter;

import java.util.ArrayList;
import java.util.List;

public class Folder extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
	public static final String AUTHORIZED = "authorized";
	private static final int REQUEST_APPLICATION = 1;
	private static final int REQUEST_UNLOCK = 2;
	private static final int REQUEST_FOLDER = 3;
	private boolean mAuthorized;
	private static final String STATE_SELECTION = "selection";
	private int mSelection;
	private String mId;
	private Folders mFolders;
	private GridView mList;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		mId = getIntent().getStringExtra(ApplicationList.APP_ID);
		mAuthorized = state == null ? getIntent().getBooleanExtra(AUTHORIZED, false) : state.getBoolean(AUTHORIZED);
		mFolders = new Folders(this);
		if (state != null)
			mSelection = state.getInt(STATE_SELECTION, 0);
		setContentView(R.layout.folder);
		mList = (GridView) findViewById(R.id.list);
		mList.setOnItemClickListener(this);
		mList.setOnItemLongClickListener(this);
		findViewById(R.id.folder_add).setOnClickListener(this);
		findViewById(R.id.folder_rename).setOnClickListener(this);
		findViewById(R.id.folder_delete).setOnClickListener(this);
		findViewById(R.id.cancel).setOnClickListener(this);
		if (new Protection(this).isLocked(mId) && !mAuthorized) {
			findViewById(R.id.folder_name).getRootView().setVisibility(View.INVISIBLE);
			if (state == null)
				startActivityForResult(Protection.getIntent(this, mId), REQUEST_FOLDER);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	protected void onPause() {
		mSelection = Math.max(0, mList.getSelectedItemPosition());
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		state.putBoolean(AUTHORIZED, mAuthorized);
		state.putInt(STATE_SELECTION, Math.max(0, mList.getSelectedItemPosition()));
		super.onSaveInstanceState(state);
	}

	@Override
	protected void onDestroy() {
		if (mDialog != null)
			mDialog.dismiss();
		super.onDestroy();
	}

	private void refresh() {
		if (new Protection(this).isLocked(mId) && !mAuthorized)
			return;
		mAuthorized = true;
		AppInfo folder = mFolders.get(mId);
		if (folder == null) {
			finish();
			return;
		}
		((TextView) findViewById(R.id.folder_name)).setText(folder.getName());
		boolean locked = new Setup(this).iconsLocked();
		findViewById(R.id.folder_add).setVisibility(locked ? View.GONE : View.VISIBLE);
		findViewById(R.id.folder_rename).setVisibility(locked ? View.GONE : View.VISIBLE);
		findViewById(R.id.folder_delete).setVisibility(locked ? View.GONE : View.VISIBLE);
		findViewById(R.id.folder_hint).setVisibility(locked ? View.GONE : View.VISIBLE);
		try {
			List<AppInfo> available = Utils.loadApplications(this);
			List<AppInfo> entries = new ArrayList<>();
			for (String id : mFolders.getItems(mId)) {
				if (!new Protection(this).isHidden(id))
					entries.add(find(available, id));
			}
			mList.setAdapter(new ApplicationAdapter(this, R.layout.grid_item, entries.toArray(new AppInfo[0])));
			if (!entries.isEmpty())
				mList.setSelection(Math.min(mSelection, entries.size() - 1));
			findViewById(R.id.folder_empty).setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.folder_error, Toast.LENGTH_LONG).show();
		}
	}

	private AppInfo find(List<AppInfo> apps, String id) {
		for (AppInfo app : apps) {
			if (id.equals(app.getId()))
				return (app);
		}
		return (new AppInfo(id, getString(R.string.application_unavailable, id), getResources().getDrawable(android.R.drawable.ic_dialog_alert)));
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		AppInfo app = (AppInfo) view.getTag();
		if (new Protection(this).isLocked(app.getId())) {
			startActivityForResult(Protection.getIntent(this, app.getId()), REQUEST_UNLOCK);
			return;
		}
		launch(app.getId(), app.getName());
	}

	private void launch(String id, String name) {
		try {
			if (Shortcuts.isShortcut(id)) {
				new Shortcuts(this).launch(id);
			} else {
				Intent intent = Utils.getLaunchIntentForPackage(this, id);
				if (intent == null)
					throw new android.content.ActivityNotFoundException();
				startActivity(intent);
			}
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.application_unavailable, name), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (new Setup(this).iconsLocked())
			return (true);
		final AppInfo app = (AppInfo) view.getTag();
		mDialog = new AlertDialog.Builder(this).setMessage(getString(R.string.folder_remove, app.getName()))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							mFolders.remove(mId, app.getId());
							refresh();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).setNegativeButton(android.R.string.cancel, null).show();
		return (true);
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.cancel) {
			finish();
		} else if (!new Setup(this).iconsLocked()) {
			if (view.getId() == R.id.folder_add) {
				Intent intent = new Intent(this, ApplicationList.class);
				intent.putExtra(ApplicationList.EXCLUDE_FOLDERS, true);
				intent.putExtra(ApplicationList.SHOW_DELETE, false);
				startActivityForResult(intent, REQUEST_APPLICATION);
			} else if (view.getId() == R.id.folder_rename) {
				rename();
			} else if (view.getId() == R.id.folder_delete) {
				confirmDelete();
			}
		}
	}

	private void rename() {
		final EditText name = new EditText(this);
		name.setSingleLine(true);
		name.setText(mFolders.get(mId).getName());
		name.setSelectAllOnFocus(true);
		final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.folder_rename)
				.setView(name).setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, null).create();
		mDialog = dialog;
		dialog.show();
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				try {
					if (mFolders.rename(mId, name.getText().toString())) {
						dialog.dismiss();
						refresh();
					} else {
						name.setError(getString(R.string.folder_name));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void confirmDelete() {
		mDialog = new AlertDialog.Builder(this).setMessage(R.string.folder_delete_confirm)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (mFolders.delete(mId))
							finish();
					}
				}).setNegativeButton(android.R.string.cancel, null).show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == REQUEST_FOLDER) {
			if (resultCode != RESULT_OK) {
				finish();
				return;
			}
			mAuthorized = true;
			findViewById(R.id.folder_name).getRootView().setVisibility(View.VISIBLE);
			refresh();
		} else if (requestCode == REQUEST_UNLOCK && resultCode == RESULT_OK && intent != null) {
			String id = intent.getStringExtra(ApplicationList.APP_ID);
			launch(id, id);
		}
		if (requestCode == REQUEST_APPLICATION && resultCode == RESULT_OK && intent != null) {
			try {
				mFolders.add(mId, intent.getStringExtra(ApplicationList.APP_ID));
				refresh();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
