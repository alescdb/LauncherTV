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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.StateSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.cosinus.launchertv.R;
import org.cosinus.launchertv.activities.Preferences;

import java.util.Locale;

public class ApplicationView extends LinearLayout {
	private ImageView mIcon;
	private TextView mText;
	private String mPackageName;
	private int mPosition;
	private float mTransparency;

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

	private float getTransparency() {
		try {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
			return (prefs.getFloat(Preferences.PREFERENCE_TRANSPARENCY, 0.5F));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (0.5F);
	}

	@SuppressWarnings("deprecation")
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private void setBackgroundStateDrawable() {
		Resources.Theme theme = getContext().getTheme();
		Drawable drawableEnabled = this.getResources().getDrawable(R.drawable.application_normal, theme);
		Drawable drawableFocused = this.getResources().getDrawable(R.drawable.application_focused, theme);
		Drawable drawablePressed = this.getResources().getDrawable(R.drawable.application_pressed, theme);

		drawableEnabled.setColorFilter(Color.argb(getTransparency(0), 0xF0, 0xF0, 0xF0), PorterDuff.Mode.SRC);
		drawableFocused.setColorFilter(Color.argb(getTransparency(0.2F), 0xE0, 0xE0, 0xFF), PorterDuff.Mode.SRC);
		drawablePressed.setColorFilter(Color.argb(getTransparency(0.2F), 0xE0, 0xE0, 0xFF), PorterDuff.Mode.SRC);

		StateListDrawable stateListDrawable = new StateListDrawable();
		stateListDrawable.addState(new int[]{
				android.R.attr.state_focused,
				android.R.attr.state_selected,
				android.R.attr.state_hovered}, drawableFocused);
		stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, drawablePressed);
		stateListDrawable.addState(StateSet.WILD_CARD, drawableEnabled);

		setBackground(stateListDrawable);
	}

	private int getTransparency(float add) {
		int trans = (int) ((mTransparency + add) * 255.0);
		if (trans > 255)
			return (255);
		if (trans < 0)
			return (0);
		return (trans);
	}

	@SuppressWarnings("UnusedParameters")
	private void initialize(Context context, AttributeSet attrs, Integer defStyle) {
		inflate(context, R.layout.application, this);
		mTransparency = getTransparency();

		setClickable(true);
		setFocusable(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			setBackgroundStateDrawable();
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
