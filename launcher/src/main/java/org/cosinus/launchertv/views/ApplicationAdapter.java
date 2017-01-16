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
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.R;

public class ApplicationAdapter extends ArrayAdapter<AppInfo> {
	private final int mResource;

	public ApplicationAdapter(Context context, int resId, AppInfo[] items) {
		super(context, R.layout.list_item, items);
		mResource = resId;
	}

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		View view;

		if (convertView == null) {
			view = View.inflate(getContext(), mResource, null);
		} else {
			view = convertView;
		}
		ImageView packageImage = (ImageView) view.findViewById(R.id.application_icon);
		TextView packageName = (TextView) view.findViewById(R.id.application_name);
		AppInfo appInfo = getItem(position);

		if (appInfo != null) {
			view.setTag(appInfo);
			packageName.setText(appInfo.getName());
			if (appInfo.getIcon() != null)
				packageImage.setImageDrawable(appInfo.getIcon());
		}
		return (view);
	}
}
