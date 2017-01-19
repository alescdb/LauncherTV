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

package org.cosinus.launchertv.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.StateSet;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;

import java.util.Locale;

import static android.content.ContentValues.TAG;

public class ApplicationView extends LinearLayout {
	private OnClickListener mMenuClickListener;
	private ImageView mIcon;
	private TextView mText;
	private String mPackageName;
	private int mPosition;

	public ApplicationView(Context context) {
		super(context);
		initialize(context, null, null);
	}

	public ApplicationView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context, attrs, null);
	}

	public ApplicationView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(context, attrs, defStyle);
	}

	public static String getPreferenceKey(int appNum) {
		return (String.format(Locale.getDefault(), "application_%02d", appNum));
	}

	private static Drawable createTileShape(int backgroundColor, int borderColor) {
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.RECTANGLE);
		shape.setCornerRadii(new float[]{7, 7, 7, 7, 0, 0, 0, 0});
		shape.setColor(backgroundColor);
		shape.setStroke(1, borderColor);
		shape.setBounds(7, 7, 7, 7);
		return (shape);
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "keyCode => " + keyCode);
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			if (mMenuClickListener != null) {
				mMenuClickListener.onClick(this);
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public void setOnMenuOnClickListener(OnClickListener clickListener) {
		mMenuClickListener = clickListener;
	}

	private void setBackgroundStateDrawable(float transparency) {
		StateListDrawable stateListDrawable = new StateListDrawable();

		Drawable drawableEnabled = createTileShape(
				Color.argb(getTransparency(transparency, 0.0F), 0xF0, 0xF0, 0xF0),
				Color.argb(0xFF, 0x90, 0x90, 0x90)
		);
		Drawable drawableFocused = createTileShape(
				Color.argb(getTransparency(transparency, 0.4F), 0xE0, 0xE0, 0xFF),
				Color.argb(0xFF, 0x90, 0x90, 0x90)
		);
		Drawable drawablePressed = createTileShape(
				Color.argb(getTransparency(transparency, 0.8F), 0xE0, 0xE0, 0xFF),
				Color.argb(0xFF, 0x00, 0x00, 0x00)
		);

		stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, drawablePressed);
		stateListDrawable.addState(new int[]{android.R.attr.state_focused}, drawableFocused);
		stateListDrawable.addState(new int[]{android.R.attr.state_hovered}, drawableFocused);
		stateListDrawable.addState(StateSet.WILD_CARD, drawableEnabled);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			setBackground(stateListDrawable);
		} else {
			//noinspection deprecation
			setBackgroundDrawable(stateListDrawable);
		}
	}

	private int getTransparency(float transparency, float add) {
		int trans = (int) ((transparency + add) * 255.0);
		if (trans > 255)
			return (255);
		if (trans < 0)
			return (0);
		return (trans);
	}

	@SuppressWarnings("UnusedParameters")
	private void initialize(Context context, AttributeSet attrs, Integer defStyle) {
		Setup setup = new Setup(context);

		inflate(context, R.layout.application, this);

		setClickable(true);
		setFocusable(true);

		if (!setup.isDefaultTransparency()) {
			setBackgroundStateDrawable(setup.getTransparency());
		} else {
			setBackgroundResource(R.drawable.application_selector);
		}

		mIcon = (ImageView) findViewById(R.id.application_icon);
		mText = (TextView) findViewById(R.id.application_name);
	}

	@SuppressWarnings("SameParameterValue")
	public ApplicationView setImageResource(@DrawableRes int res) {
		mIcon.setImageResource(res);
		return (this);
	}

	public ApplicationView setImageDrawable(Drawable drawable) {
		mIcon.setImageDrawable(drawable);
		return (this);
	}

	public ApplicationView setText(CharSequence text) {
		mText.setText(text);
		return (this);
	}

	public void showName(boolean show) {
		mText.setVisibility(
				show ? VISIBLE : GONE
		);
	}

	public String getPackageName() {
		return mPackageName;
	}

	@SuppressWarnings("UnusedReturnValue")
	public ApplicationView setPackageName(String packageName) {
		mPackageName = packageName;
		return (this);
	}

	public String getName() {
		return mText.getText().toString();
	}

	public boolean hasPackage() {
		return !TextUtils.isEmpty(mPackageName);
	}

	public int getPosition() {
		return mPosition;
	}

	public void setPosition(int position) {
		mPosition = position;
	}

	public String getPreferenceKey() {
		return (getPreferenceKey(getPosition()));
	}

}
