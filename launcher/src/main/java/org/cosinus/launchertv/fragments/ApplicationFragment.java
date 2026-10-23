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

package org.cosinus.launchertv.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;
import org.cosinus.launchertv.Network;
import org.cosinus.launchertv.Shortcuts;
import org.cosinus.launchertv.Folders;
import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.activities.Folder;
import org.cosinus.launchertv.Utils;
import org.cosinus.launchertv.activities.AddShortcut;
import org.cosinus.launchertv.activities.ApplicationList;
import org.cosinus.launchertv.activities.Preferences;
import org.cosinus.launchertv.views.ApplicationView;

import java.text.DateFormat;
import java.util.Date;

@SuppressWarnings("PointlessBooleanExpression")
public class ApplicationFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
	public static final String TAG = "ApplicationFragment";
	private static final String STATE_APPLICATION = "focused_application";
	private static final String STATE_BUTTON = "focused_button";
	private static final int REQUEST_CODE_APPLICATION_LIST = 0x1E;
	private static final int REQUEST_CODE_WALLPAPER = 0x1F;
	private static final int REQUEST_CODE_APPLICATION_START = 0x20;
	private static final int REQUEST_CODE_UNLOCK = 0x22;

	private ImageView mNetworkIcon;
	private TextView mIp;
	private Network mNetwork;
	private TextView mClock;
	private TextView mDate;
	private DateFormat mTimeFormat;
	private DateFormat mDateFormat;
	private TextView mBatteryLevel;
	private ImageView mBatteryIcon;
	private BroadcastReceiver mBatteryChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			mBatteryLevel.setText(
					String.format(getResources().getString(R.string.battery_level_text), level)
			);
			final int batteryIconId = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);
			mBatteryIcon.setImageDrawable(getResources().getDrawable(batteryIconId));
		}
	};
	private boolean mBatteryChangedReceiverRegistered = false;

	private final Handler mHandler = new Handler();
	private final Runnable mTimerTick = new Runnable() {
		@Override
		public void run() {
			setClock();
		}
	};

	private int mGridX = 3;
	private int mGridY = 2;
	private int mMarginX;
	private int mMarginY;
	private boolean mShowNames;
	private boolean mDefaultTransparency;
	private float mTransparency;
	private LinearLayout mBatteryLayout;
	private LinearLayout mContainer;
	private ApplicationView[][] mApplications = null;
	private View mSettings;
	private View mGridView;
	private Setup mSetup;
	private boolean mShowingShortcut = false;
	private final SharedPreferences.OnSharedPreferenceChangeListener mShortcutChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
			if (Shortcuts.isPending(key))
				showShortcut();
		}
	};
	private boolean mPackageChangedReceiverRegistered = false;
	private final BroadcastReceiver mPackageChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateApplications();
		}
	};


	public ApplicationFragment() {
		// Required empty public constructor
	}

	public static ApplicationFragment newInstance() {
		return new ApplicationFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_application, container, false);

		mSetup = new Setup(getContext());
		mContainer = (LinearLayout) view.findViewById(R.id.container);
		mSettings = view.findViewById(R.id.settings);
		mGridView = view.findViewById(R.id.application_grid);
		mIp = (TextView) view.findViewById(R.id.ip_address);
		mIp.setOnClickListener(this);
		mNetworkIcon = (ImageView) view.findViewById(R.id.network_icon);
		mNetwork = new Network(getContext(), mIp, mNetworkIcon);
		mClock = (TextView) view.findViewById(R.id.clock);
		mDate = (TextView) view.findViewById(R.id.date);
		mBatteryLayout = (LinearLayout) view.findViewById(R.id.battery_layout);
		mBatteryLevel = (TextView) view.findViewById(R.id.battery_level);
		mBatteryIcon = (ImageView) view.findViewById(R.id.battery_icon);

		mTimeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());

		mSettings.setOnClickListener(this);
		mGridView.setOnClickListener(this);

		createApplications();

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (getView() == null)
			return;
		View focused = getView().findFocus();
		if (focused instanceof ApplicationView) {
			state.putInt(STATE_APPLICATION, ((ApplicationView) focused).getPosition());
		} else if (focused != null) {
			state.putInt(STATE_BUTTON, focused.getId());
		}
	}

	@Override
	public void onViewStateRestored(Bundle state) {
		super.onViewStateRestored(state);
		if (state == null)
			return;
		int position = state.getInt(STATE_APPLICATION, -1);
		if (position >= 0 && position < mGridX * mGridY) {
			mApplications[position / mGridX][position % mGridX].requestFocus();
		} else {
			View focused = getView().findViewById(state.getInt(STATE_BUTTON, View.NO_ID));
			if (focused != null)
				focused.requestFocus();
		}
	}

	private void createApplications() {
		mContainer.removeAllViews();

		mGridX = mSetup.getGridX();
		mGridY = mSetup.getGridY();

		if (mGridX < 2)
			mGridX = 2;
		if (mGridY < 1)
			mGridY = 1;

		mMarginX = mSetup.getMarginX();
		mMarginY = mSetup.getMarginY();
		mShowNames = mSetup.showNames();
		mDefaultTransparency = mSetup.isDefaultTransparency();
		mTransparency = mSetup.getTransparency();
		int marginX = Utils.getPixelFromDp(getContext(), mMarginX);
		int marginY = Utils.getPixelFromDp(getContext(), mMarginY);

		mApplications = new ApplicationView[mGridY][mGridX];

		int position = 0;
		for (int y = 0; y < mGridY; y++) {
			LinearLayout ll = new LinearLayout(getContext());
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setGravity(Gravity.TOP);
			ll.setFocusable(false);
			ll.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
			));

			for (int x = 0; x < mGridX; x++) {
				ApplicationView av = new ApplicationView(getContext());
				av.setOnClickListener(this);
				av.setOnLongClickListener(this);
				av.setOnMenuOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onLongClick(v);
					}
				});
				av.setPosition(position++);
				av.showName(mShowNames);
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
					av.setId(0x00FFFFFF + position);
				} else {
					av.setId(View.generateViewId());
				}
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
				lp.setMargins(x == 0 ? 0 : marginX, y == 0 ? 0 : marginY,
						x + 1 == mGridX ? 0 : marginX, y + 1 == mGridY ? 0 : marginY);
				av.setLayoutParams(lp);
				ll.addView(av);
				mApplications[y][x] = av;
			}
			mContainer.addView(ll);
		}

		updateApplications();
		setApplicationOrder();
	}

	private void setApplicationOrder() {
		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				int upId = x + 1 == mGridX ? R.id.settings : R.id.application_grid;
				int downId = mSetup.showIp() ? R.id.ip_address : upId;
				int leftId = R.id.application_grid;
				int rightId = R.id.settings;

				if (y > 0)
					upId = mApplications[y - 1][x].getId();

				if (y + 1 < mGridY)
					downId = mApplications[y + 1][x].getId();

				if (x > 0)
					leftId = mApplications[y][x - 1].getId();
				else if (y > 0)
					leftId = mApplications[y - 1][mGridX - 1].getId();

				if (x + 1 < mGridX)
					rightId = mApplications[y][x + 1].getId();
				else if (y + 1 < mGridY)
					rightId = mApplications[y + 1][0].getId();

				mApplications[y][x].setNextFocusLeftId(leftId);
				mApplications[y][x].setNextFocusRightId(rightId);
				mApplications[y][x].setNextFocusUpId(upId);
				mApplications[y][x].setNextFocusDownId(downId);
			}
		}

		mGridView.setNextFocusLeftId(R.id.settings);
		mGridView.setNextFocusRightId(R.id.settings);
		mGridView.setNextFocusUpId(R.id.application_grid);
		mGridView.setNextFocusDownId(mApplications[0][0].getId());

		mIp.setNextFocusLeftId(mApplications[mGridY - 1][mGridX - 1].getId());
		mIp.setNextFocusRightId(R.id.settings);
		mIp.setNextFocusUpId(mApplications[mGridY - 1][0].getId());
		mIp.setNextFocusDownId(R.id.application_grid);
		mSettings.setNextFocusLeftId(R.id.application_grid);
		mSettings.setNextFocusRightId(R.id.application_grid);
		mSettings.setNextFocusUpId(R.id.settings);
		mSettings.setNextFocusDownId(mApplications[0][mGridX - 1].getId());
	}


	private void updateApplications() {
		PackageManager pm = getActivity().getPackageManager();
		SharedPreferences prefs = getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE);

		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				ApplicationView app = mApplications[y][x];
				setApplication(pm, app, prefs.getString(app.getPreferenceKey(), null));
			}
		}
	}


	private boolean hasGridChanged() {
		return (mGridX != Math.max(2, mSetup.getGridX()) || mGridY != Math.max(1, mSetup.getGridY())
				|| mMarginX != mSetup.getMarginX() || mMarginY != mSetup.getMarginY()
				|| mShowNames != mSetup.showNames() || mDefaultTransparency != mSetup.isDefaultTransparency()
				|| (!mDefaultTransparency && mTransparency != mSetup.getTransparency()));
	}

	private void updatePreferences() {
		View focused = getView().findFocus();
		int position = focused instanceof ApplicationView ? ((ApplicationView) focused).getPosition() : -1;
		mContainer.setKeepScreenOn(mSetup.keepScreenOn());
		mDate.setVisibility(mSetup.showDate() ? View.VISIBLE : View.GONE);
		mBatteryLayout.setVisibility(mSetup.showBattery() ? View.VISIBLE : View.INVISIBLE);
		mIp.setVisibility(mSetup.showIp() ? View.VISIBLE : View.GONE);
		mNetworkIcon.setVisibility(mSetup.showNetwork() ? View.VISIBLE : View.GONE);
		if (hasGridChanged())
			createApplications();
		else
			setApplicationOrder();
		if (position >= 0) {
			position = Math.min(position, mGridX * mGridY - 1);
			mApplications[position / mGridX][position % mGridX].requestFocus();
		} else if (focused != null) {
			if (focused.isShown())
				focused.requestFocus();
			else
				mSettings.requestFocus();
		}
	}


	private void writePreferences(int appNum, String appId) {
		SharedPreferences prefs = getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		String key = ApplicationView.getPreferenceKey(appNum);

		if (TextUtils.isEmpty(appId))
			editor.remove(key);
		else
			editor.putString(key, appId);

		editor.apply();
	}

	private void setApplication(PackageManager pm, ApplicationView app, String appId) {
		if (new Protection(getContext()).isHidden(appId))
			appId = null;
		try {
			if (Shortcuts.isShortcut(appId) || Folders.isFolder(appId)) {
				AppInfo shortcut = Folders.isFolder(appId) ? new Folders(getContext()).get(appId) : new Shortcuts(getContext()).get(appId);
				if (shortcut == null)
					setUnavailableApplication(app, appId);
				else
					app.setImageDrawable(shortcut.getIcon()).setText(shortcut.getName()).setAppId(appId);
				return;
			}

			if (TextUtils.isEmpty(appId) == false) {
				PackageInfo pi = pm.getPackageInfo(appId, 0);
				if (pi.applicationInfo.enabled) {
					AppInfo appInfo = new AppInfo(pm, pi.applicationInfo);
					app.setImageDrawable(appInfo.getIcon())
							.setText(appInfo.getName())
							.setAppId(appInfo.getPackageName());
				} else {
					setUnavailableApplication(app, appId);
				}
			} else {
				app.setImageResource(R.drawable.ic_add)
						.setText("")
						.setAppId(null);
			}
		} catch (PackageManager.NameNotFoundException e) {
			setUnavailableApplication(app, appId);
		} catch (Exception e) {
			e.printStackTrace();
			setUnavailableApplication(app, appId);
		}
	}

	private void setUnavailableApplication(ApplicationView app, String appId) {
		app.setImageResource(android.R.drawable.ic_dialog_alert)
				.setText(getString(R.string.application_unavailable, appId))
				.setAppId(appId);
	}

	private void showShortcut() {
		if (!isResumed() || mShowingShortcut)
			return;
		String pending = new Shortcuts(getContext()).getPending();
		if (pending != null) {
			mShowingShortcut = true;
			Intent intent = new Intent(getActivity(), AddShortcut.class);
			intent.putExtra(AddShortcut.SHORTCUT_ID, pending);
			startActivity(intent);
		}
	}

	private void registerPackageReceiver() {
		if (mPackageChangedReceiverRegistered)
			return;
		IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
		filter.addDataScheme("package");
		getActivity().registerReceiver(mPackageChangedReceiver, filter);
		mPackageChangedReceiverRegistered = true;
		IntentFilter storageFilter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		storageFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		getActivity().registerReceiver(mPackageChangedReceiver, storageFilter);
	}

	@Override
	public void onResume() {
		super.onResume();
		updatePreferences();
		if (mSetup.showIp() || mSetup.showNetwork())
			mNetwork.start();
		else
			mNetwork.stop();
		mTimeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());
		setClock();
		updateApplications();
		getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(mShortcutChangedListener);
		mShowingShortcut = false;
		showShortcut();
		registerPackageReceiver();
		if (mSetup.showBattery() && !mBatteryChangedReceiverRegistered) {
			getActivity().registerReceiver(this.mBatteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			mBatteryChangedReceiverRegistered = true;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		mNetwork.stop();
		getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(mShortcutChangedListener);
		mHandler.removeCallbacks(mTimerTick);
		if (mBatteryChangedReceiverRegistered) {
			getActivity().unregisterReceiver(this.mBatteryChangedReceiver);
			mBatteryChangedReceiverRegistered = false;
		}
		if (mPackageChangedReceiverRegistered) {
			getActivity().unregisterReceiver(mPackageChangedReceiver);
			mPackageChangedReceiverRegistered = false;
		}
	}

	@Override
	public void onDestroyView() {
		if (mNetwork != null)
			mNetwork.stop();
		mNetwork = null;
		mIp = null;
		mNetworkIcon = null;
		mBatteryLayout = null;
		super.onDestroyView();
	}

	private void setClock() {
		Date date = new Date(System.currentTimeMillis());
		String time = mTimeFormat.format(date);
		String day = mDateFormat.format(date);
		if (!TextUtils.equals(mClock.getText(), time))
			mClock.setText(time);
		if (!TextUtils.equals(mDate.getText(), day))
			mDate.setText(day);
		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public boolean onLongClick(View v) {
		if (v instanceof ApplicationView) {
			ApplicationView appView = (ApplicationView) v;
			if (mSetup.iconsLocked()) {
				Toast.makeText(getActivity(), R.string.home_locked, Toast.LENGTH_SHORT).show();
			} else {
				openApplicationList(ApplicationList.VIEW_LIST, appView.getPosition(), appView.hasApp(), REQUEST_CODE_APPLICATION_LIST);
			}
			return (true);
		}
		return (false);
	}

	@Override
	public void onClick(View v) {
		if (v instanceof ApplicationView) {
			openApplication((ApplicationView) v);
			return;
		}

		int id = v.getId();
		if (id == R.id.ip_address) {
			mNetwork.showDetails();
		} else if (id == R.id.application_grid) {
			openApplicationList(ApplicationList.VIEW_GRID, 0, false, REQUEST_CODE_APPLICATION_START);
		} else if (id == R.id.settings) {
			startActivity(new Intent(getContext(), Preferences.class));
		}

	}

	private void openApplication(ApplicationView v) {
		if (v.hasApp() == false) {
			if (mSetup.iconsLocked()) {
				Toast.makeText(getActivity(), R.string.home_locked, Toast.LENGTH_SHORT).show();
			} else {
				openApplicationList(ApplicationList.VIEW_LIST, v.getPosition(), false, REQUEST_CODE_APPLICATION_LIST);
			}
			return;
		}

		openApplication(v.getAppId(), v.getName());
	}

	private void openApplication(String appId) {
		openApplication(appId, appId);
	}

	private void openApplication(String appId, String name) {
		if (new Protection(getContext()).isLocked(appId)) {
			startActivityForResult(Protection.getIntent(getContext(), appId), REQUEST_CODE_UNLOCK);
			return;
		}
		launchApplication(appId, name);
	}

	private void launchApplication(String appId, String name) {
		try {
			if (Folders.isFolder(appId)) {
				Intent intent = new Intent(getActivity(), Folder.class);
				intent.putExtra(ApplicationList.APP_ID, appId);
				intent.putExtra(Folder.AUTHORIZED, true);
				startActivity(intent);
				return;
			}
			if (Shortcuts.isShortcut(appId)) {
				new Shortcuts(getContext()).launch(appId);
				return;
			}
			Intent startApp = Utils.getLaunchIntentForPackage(getContext(), appId);
			if (startApp == null) {
				Toast.makeText(getActivity(), getString(R.string.application_unavailable, appId), Toast.LENGTH_LONG).show();
				return;
			}
			Toast.makeText(getActivity(), name, Toast.LENGTH_SHORT).show();
			startActivity(startApp);
		} catch (Exception e) {
			String message = Shortcuts.isShortcut(appId) ? getString(R.string.application_unavailable, name) : name + " : " + e.getMessage();
			Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
		}
	}

	private void openApplicationList(int viewType, int appNum, boolean showDelete, int requestCode) {
		Intent intent = new Intent(getActivity(), ApplicationList.class);
		intent.putExtra(ApplicationList.APPLICATION_NUMBER, appNum);
		intent.putExtra(ApplicationList.VIEW_TYPE, viewType);
		intent.putExtra(ApplicationList.SHOW_DELETE, showDelete);
		intent.putExtra(ApplicationList.CREATE_FOLDER, requestCode == REQUEST_CODE_APPLICATION_LIST);
		startActivityForResult(intent, requestCode);
	}
	

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch (requestCode) {
			case REQUEST_CODE_UNLOCK:
				if (resultCode == Activity.RESULT_OK && intent != null) {
					String id = intent.getStringExtra(ApplicationList.APP_ID);
					launchApplication(id, id);
				}
				break;
			case REQUEST_CODE_WALLPAPER:
				break;
			case REQUEST_CODE_APPLICATION_START:
				if (intent != null)
					openApplication(intent.getExtras().getString(ApplicationList.APP_ID));
				break;
			case REQUEST_CODE_APPLICATION_LIST:
				if (resultCode == Activity.RESULT_OK) {
					Bundle extra = intent.getExtras();
					int appNum = intent.getExtras().getInt(ApplicationList.APPLICATION_NUMBER);

					if (extra.containsKey(ApplicationList.DELETE) && extra.getBoolean(ApplicationList.DELETE)) {
						writePreferences(appNum, null);
					} else {
						writePreferences(appNum,
								intent.getExtras().getString(ApplicationList.APP_ID)
						);
					}
					updateApplications();
				}
				break;
		}
	}


}
