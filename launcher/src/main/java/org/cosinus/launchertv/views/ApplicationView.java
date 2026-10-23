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
import android.view.View;
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
	private String mAppId;
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

	private Drawable createTileShape(@DrawableRes int res, int backgroundColor) {
		GradientDrawable shape = (GradientDrawable) getResources().getDrawable(res).mutate();
		shape.setColor(backgroundColor);
		return (shape);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
			int size = getIconSize(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
			LayoutParams params = (LayoutParams) mIcon.getLayoutParams();
			params.width = size;
			params.height = size;
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private int getIconSize(int width, int height) {
		View content = (View) mIcon.getParent();
		width -= getPaddingLeft() + getPaddingRight() + content.getPaddingLeft() + content.getPaddingRight();
		height -= getPaddingTop() + getPaddingBottom() + content.getPaddingTop() + content.getPaddingBottom();
		if (mText.getVisibility() != GONE) {
			mText.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.AT_MOST),
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			LayoutParams params = (LayoutParams) mText.getLayoutParams();
			height -= mText.getMeasuredHeight() + params.topMargin + params.bottomMargin;
		}
		int size = getResources().getDimensionPixelSize(R.dimen.application_drawable_size);
		return (Math.max(0, Math.min(size, Math.min(width, height))));
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
				R.drawable.application_normal,
				Color.argb(getTransparency(transparency, 0.0F), 0x21, 0x2A, 0x36)
		);
		Drawable drawableFocused = createTileShape(
				R.drawable.application_focused,
				Color.argb(getTransparency(transparency, 0.4F), 0x3A, 0x46, 0x57)
		);
		Drawable drawablePressed = createTileShape(
				R.drawable.application_pressed,
				Color.argb(getTransparency(transparency, 0.8F), 0x4A, 0x58, 0x6B)
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

	public String getAppId() {
		return mAppId;
	}

	@SuppressWarnings("UnusedReturnValue")
	public ApplicationView setAppId(String appId) {
		mAppId = appId;
		return (this);
	}

	public String getName() {
		return mText.getText().toString();
	}

	public boolean hasApp() {
		return !TextUtils.isEmpty(mAppId);
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
