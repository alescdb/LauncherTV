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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.Folders;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Utils;
import org.cosinus.launchertv.views.ApplicationAdapter;

import java.util.Iterator;
import java.util.List;
import java.lang.ref.WeakReference;


public class ApplicationList extends Activity implements AdapterView.OnItemClickListener, View.OnClickListener {
	public static final String CREATE_FOLDER = "create_folder";
	public static final String EXCLUDE_FOLDERS = "exclude_folders";
	public static final String APP_ID = "app_id";
	public static final String APPLICATION_NUMBER = "application";
	public static final String VIEW_TYPE = "view_type";
	public static final String DELETE = "delete";
	public static final String SHOW_DELETE = "show_delete";
	//
	public static final int VIEW_GRID = 0;
	public static final int VIEW_LIST = 1;
	//
	private static final String NEW_FOLDER = "new_folder";
	private int mApplication = -1;
	private int mViewType = 0;
	private AbsListView mListView;
	private AlertDialog mFolderDialog;
	private LoadTask mApplicationLoader;

	private static class LoadTask extends AsyncTask<Void, Void, AppInfo[]> {
		private final WeakReference<ApplicationList> mOwner;
		private final Context mContext;
		private final boolean mExcludeFolders;
		private final boolean mCreateFolder;

		LoadTask(ApplicationList owner, boolean excludeFolders, boolean createFolder) {
			mOwner = new WeakReference<>(owner);
			mContext = owner.getApplicationContext();
			mExcludeFolders = excludeFolders;
			mCreateFolder = createFolder;
		}

		@Override
		protected AppInfo[] doInBackground(Void... params) {
			if (isCancelled())
				return (null);
			List<AppInfo> apps = Utils.loadApplications(mContext);
			if (isCancelled())
				return (null);
			if (mExcludeFolders) {
				Iterator<AppInfo> entries = apps.iterator();
				while (entries.hasNext()) {
					if (Folders.isFolder(entries.next().getId()))
						entries.remove();
				}
			}
			if (mCreateFolder)
				apps.add(0, new AppInfo(NEW_FOLDER, mContext.getString(R.string.new_folder), mContext.getResources().getDrawable(R.drawable.ic_folder)));
			return (apps.toArray(new AppInfo[0]));
		}

		@Override
		protected void onPostExecute(AppInfo[] apps) {
			ApplicationList owner = mOwner.get();
			if (owner == null || owner.isFinishing() || isCancelled() || owner.mApplicationLoader != this)
				return;
			owner.mApplicationLoader = null;
			owner.showApplications(apps);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		Bundle args = intent.getExtras();

		if (args != null) {
			if (args.containsKey(APPLICATION_NUMBER))
				mApplication = args.getInt(APPLICATION_NUMBER);
			if (args.containsKey(VIEW_TYPE))
				mViewType = args.getInt(VIEW_TYPE);
		}

		setContentView(mViewType == VIEW_LIST ?
				R.layout.listview :
				R.layout.gridview);

		mListView = (AbsListView) findViewById(R.id.list);
		mApplicationLoader = new LoadTask(this, intent.getBooleanExtra(EXCLUDE_FOLDERS, false), intent.getBooleanExtra(CREATE_FOLDER, false));
		mApplicationLoader.execute();

		View v;
		if ((args != null) && (args.containsKey(SHOW_DELETE))) {
			if (!args.getBoolean(SHOW_DELETE)) {
				if ((v = findViewById(R.id.bottom_panel)) != null)
					v.setVisibility(View.GONE);
			}
		}
		if ((v = findViewById(R.id.delete)) != null)
			v.setOnClickListener(this);
		if ((v = findViewById(R.id.cancel)) != null)
			v.setOnClickListener(this);
	}

	@Override
	protected void onDestroy() {
		if (mApplicationLoader != null) {
			mApplicationLoader.cancel(true);
			mApplicationLoader = null;
		}
		if (mFolderDialog != null)
			mFolderDialog.dismiss();
		super.onDestroy();
	}

	private void showApplications(AppInfo[] apps) {
		getListView().setOnItemClickListener(this);
		getListView().setAdapter(new ApplicationAdapter(this,
				mViewType == VIEW_LIST ? R.layout.list_item : R.layout.grid_item, apps));
	}

	private AbsListView getListView() {
		return mListView;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		AppInfo appInfo = (AppInfo) view.getTag();
		if (NEW_FOLDER.equals(appInfo.getId())) {
			createFolder();
			return;
		}
		select(appInfo.getId());
	}

	private void select(String appId) {
		Intent data = new Intent();
		data.putExtra(APP_ID, appId);
		data.putExtra(APPLICATION_NUMBER, mApplication);

		if (getParent() == null) {
			setResult(Activity.RESULT_OK, data);
		} else {
			getParent().setResult(Activity.RESULT_OK, data);
		}
		finish();
	}

	private void createFolder() {
		final EditText name = new EditText(this);
		name.setSingleLine(true);
		name.setHint(R.string.folder_name);
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.new_folder).setView(name)
				.setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, null).create();
		mFolderDialog = dialog;
		dialog.show();
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				try {
					String id = new Folders(ApplicationList.this).create(name.getText().toString());
					if (id != null) {
						dialog.dismiss();
						select(id);
					} else {
						name.setError(getString(R.string.folder_name));
					}
				} catch (org.json.JSONException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.delete) {
			Intent data = new Intent();
			data.putExtra(DELETE, true);
			data.putExtra(APPLICATION_NUMBER, mApplication);
			if (getParent() == null)
				setResult(Activity.RESULT_OK, data);
			else
				getParent().setResult(Activity.RESULT_OK, data);
			finish();
		} else if (id == R.id.cancel) {
			if (getParent() == null)
				setResult(Activity.RESULT_CANCELED);
			else
				getParent().setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}
}
