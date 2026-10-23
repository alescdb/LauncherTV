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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.Folders;
import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ProtectionSettings extends PreferenceActivity implements Preference.OnPreferenceClickListener {
	private static final int REQUEST_UNLOCK = 1;
	private boolean mAuthorized;
	private Protection mProtection;
	private AlertDialog mDialog;
	private char[] mNewPin;
	private AsyncTask<Void, Void, Boolean> mSave;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		mProtection = new Protection(this);
		mAuthorized = state != null && state.getBoolean("authorized");
		if (mProtection.isEnabled() && !mAuthorized) {
			if (state == null)
				startActivityForResult(Protection.getIntent(this, null), REQUEST_UNLOCK);
			return;
		}
		initialize();
	}

	private void initialize() {
		mAuthorized = true;
		addPreferencesFromResource(R.xml.protection);
		findPreference("pin").setOnPreferenceClickListener(this);
		findPreference("disable").setOnPreferenceClickListener(this);
		findPreference(Protection.HIDDEN).setOnPreferenceClickListener(this);
		findPreference(Protection.LOCKED).setOnPreferenceClickListener(this);
		refresh();
	}

	private void refresh() {
		boolean enabled = mProtection.isEnabled();
		findPreference("pin").setTitle(enabled ? R.string.pin_change : R.string.pin_enable);
		findPreference("disable").setEnabled(enabled);
		findPreference(Protection.HIDDEN).setEnabled(enabled);
		findPreference(Protection.LOCKED).setEnabled(enabled);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String key = preference.getKey();
		if ("pin".equals(key)) {
			clearPin();
			askPin(false);
		} else if ("disable".equals(key)) {
			if (mProtection.disable())
				refresh();
		} else {
			chooseItems(key);
		}
		return (true);
	}

	private void chooseItems(final String key) {
		final List<AppInfo> apps = new ArrayList<>();
		for (AppInfo app : Utils.loadApplications(this, true)) {
			if (Protection.LOCKED.equals(key) || !Folders.isFolder(app.getId()))
				apps.add(app);
		}
		final Set<String> selected = mProtection.getItems(key);
		String[] names = new String[apps.size()];
		boolean[] checked = new boolean[apps.size()];
		for (int i = 0; i < apps.size(); i++) {
			names[i] = apps.get(i).getName();
			checked[i] = selected.contains(apps.get(i).getId());
		}
		mDialog = new AlertDialog.Builder(this)
				.setTitle(Protection.HIDDEN.equals(key) ? R.string.hidden_apps : R.string.locked_apps)
				.setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean value) {
						String id = apps.get(which).getId();
						if (value)
							selected.add(id);
						else
							selected.remove(id);
					}
				}).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (!mProtection.setItems(key, selected))
							Toast.makeText(ProtectionSettings.this, R.string.protection_error, Toast.LENGTH_LONG).show();
					}
				}).setNegativeButton(android.R.string.cancel, null).show();
	}

	private void askPin(final boolean confirm) {
		final EditText pin = new EditText(this);
		pin.setSingleLine(true);
		pin.setSaveEnabled(false);
		pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
		pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Protection.MAX_LENGTH)});
		pin.setHint(R.string.pin);
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(confirm ? R.string.pin_confirm : R.string.pin_enable).setMessage(R.string.pin_format).setView(pin)
				.setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface view, int which) {
						clearPin();
					}
				}).setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface view) {
						clearPin();
					}
				}).create();
		mDialog = dialog;
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		dialog.show();
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				submitPin(dialog, pin, confirm);
			}
		});
	}

	private void submitPin(AlertDialog dialog, EditText pin, boolean confirm) {
		char[] value = new char[pin.length()];
		pin.getText().getChars(0, value.length, value, 0);
		pin.setText("");
		if (value.length < Protection.MIN_LENGTH) {
			Arrays.fill(value, '\0');
			dialog.setMessage(getString(R.string.pin_format));
		} else if (!confirm) {
			mNewPin = value;
			dialog.dismiss();
			askPin(true);
		} else {
			boolean matches = Arrays.equals(mNewPin, value);
			Arrays.fill(value, '\0');
			if (matches)
				savePin(dialog);
			else
				dialog.setMessage(getString(R.string.pin_mismatch));
		}
	}

	private void savePin(final AlertDialog dialog) {
		if (mSave != null)
			return;
		final char[] pin = mNewPin;
		mNewPin = null;
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
		mSave = new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... args) {
				try {
					mProtection.setPin(pin);
					return (true);
				} catch (Exception e) {
					return (false);
				} finally {
					Arrays.fill(pin, '\0');
				}
			}

			@Override
			protected void onPostExecute(Boolean saved) {
				mSave = null;
				if (isFinishing())
					return;
				dialog.dismiss();
				if (saved) {
					mAuthorized = true;
					refresh();
				} else {
					Toast.makeText(ProtectionSettings.this, R.string.protection_error, Toast.LENGTH_LONG).show();
				}
			}
		};
		mSave.execute();
	}

	private void clearPin() {
		if (mNewPin != null)
			Arrays.fill(mNewPin, '\0');
		mNewPin = null;
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		state.putBoolean("authorized", mAuthorized);
		super.onSaveInstanceState(state);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == REQUEST_UNLOCK) {
			if (resultCode == RESULT_OK) {
				mAuthorized = true;
				initialize();
			} else {
				finish();
			}
		}
	}

	@Override
	protected void onDestroy() {
		clearPin();
		if (mSave != null)
			mSave.cancel(true);
		if (mDialog != null)
			mDialog.dismiss();
		super.onDestroy();
	}
}
