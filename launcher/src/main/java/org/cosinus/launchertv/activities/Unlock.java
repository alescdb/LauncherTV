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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.InputFilter;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.R;

import java.util.Arrays;

public class Unlock extends Activity implements View.OnClickListener, DialogInterface.OnCancelListener {
	private EditText mPin;
	private AlertDialog mDialog;
	private AsyncTask<Void, Void, Boolean> mCheck;
	private Protection mProtection;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		mProtection = new Protection(this);
		if (!mProtection.isEnabled()) {
			finish();
			return;
		}
		mPin = new EditText(this);
		mPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
		mPin.setSingleLine(true);
		mPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Protection.MAX_LENGTH)});
		mPin.setHint(R.string.pin);
		mPin.setSaveEnabled(false);
		mDialog = new AlertDialog.Builder(this).setTitle(R.string.unlock).setMessage(R.string.pin_format)
				.setView(mPin).setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).setOnCancelListener(this).create();
		mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		mDialog.show();
		mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (mCheck != null)
			return;
		if (mProtection.getRetryDelay() > 0) {
			mDialog.setMessage(getString(R.string.pin_retry));
			return;
		}
		final char[] pin = new char[mPin.length()];
		mPin.getText().getChars(0, pin.length, pin, 0);
		mPin.setText("");
		mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
		mCheck = new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... args) {
				try {
					return (mProtection.verify(pin));
				} catch (Exception e) {
					return (false);
				} finally {
					Arrays.fill(pin, '\0');
				}
			}

			@Override
			protected void onPostExecute(Boolean valid) {
				mCheck = null;
				if (isFinishing())
					return;
				if (valid) {
					Intent data = new Intent();
					data.putExtra(ApplicationList.APP_ID, getIntent().getStringExtra(ApplicationList.APP_ID));
					setResult(RESULT_OK, data);
					finish();
				} else {
					mDialog.setMessage(getString(mProtection.getRetryDelay() > 0 ? R.string.pin_retry : R.string.pin_invalid));
					mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
				}
			}
		};
		mCheck.execute();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		finish();
	}

	@Override
	protected void onDestroy() {
		if (mCheck != null)
			mCheck.cancel(true);
		if (mDialog != null)
			mDialog.dismiss();
		super.onDestroy();
	}
}
