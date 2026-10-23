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
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.PinShortcut;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.Setup;
import org.cosinus.launchertv.Shortcuts;

public class AddShortcut extends Activity implements DialogInterface.OnClickListener {
	public static final String SHORTCUT_ID = "shortcut_id";
	private static final int REQUEST_UNLOCK = 1;
	private boolean mAuthorized;
	private String mPending;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		mPending = getIntent().getStringExtra(SHORTCUT_ID);
		mAuthorized = state != null && state.getBoolean("authorized");
		if (new Protection(this).isEnabled() && !mAuthorized) {
			if (state == null)
				startActivityForResult(Protection.getIntent(this, null), REQUEST_UNLOCK);
			return;
		}
		showConfirmation();
	}

	private void showConfirmation() {
		mAuthorized = true;
		AppInfo shortcut = getShortcut();
		if (shortcut == null || !canAdd()) {
			if (shortcut == null)
				Toast.makeText(this, R.string.shortcut_unavailable, Toast.LENGTH_LONG).show();
			closeRequest();
			return;
		}
		mDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.add_shortcut)
				.setMessage(getString(R.string.confirm_shortcut, shortcut.getName()))
				.setIcon(shortcut.getIcon())
				.setPositiveButton(android.R.string.ok, this)
				.setNegativeButton(android.R.string.cancel, this)
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						closeRequest();
					}
				}).show();
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
				showConfirmation();
			} else {
				closeRequest();
			}
		}
	}

	private AppInfo getShortcut() {
		try {
			if (Shortcuts.isPending(mPending))
				return (new Shortcuts(this).get(mPending));
			if (mPending == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				return (PinShortcut.get(this, getIntent()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (null);
	}

	private boolean canAdd() {
		if (new Setup(this).iconsLocked()) {
			Toast.makeText(this, R.string.home_locked, Toast.LENGTH_LONG).show();
			return (false);
		}
		if (new Shortcuts(this).findEmpty() < 0) {
			Toast.makeText(this, R.string.no_empty_tile, Toast.LENGTH_LONG).show();
			return (false);
		}
		return (true);
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE && canAdd()) {
			try {
				boolean added = false;
				if (mPending != null)
					added = new Shortcuts(this).accept(mPending);
				else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					added = PinShortcut.accept(this, getIntent());
				if (!added)
					Toast.makeText(this, R.string.shortcut_unavailable, Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(this, R.string.shortcut_unavailable, Toast.LENGTH_LONG).show();
			}
		}
		closeRequest();
	}

	private void closeRequest() {
		new Shortcuts(this).discard(mPending);
		finish();
	}

	@Override
	protected void onDestroy() {
		if (mDialog != null)
			mDialog.dismiss();
		super.onDestroy();
	}
}
