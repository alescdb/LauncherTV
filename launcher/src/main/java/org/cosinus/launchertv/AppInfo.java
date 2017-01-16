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

package org.cosinus.launchertv;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;


public class AppInfo {
	private final Drawable mIcon;
	private String mName;
	private final String mPackageName;

	AppInfo(PackageManager packageManager, ResolveInfo resolveInfo) {
		mPackageName = resolveInfo.activityInfo.packageName;
		mIcon = resolveInfo.loadIcon(packageManager);
		try {
			mName = resolveInfo.loadLabel(packageManager).toString();
		} catch (Exception e) {
			mName = mPackageName;
		}
	}

	public AppInfo(PackageManager packageManager, ApplicationInfo applicationInfo) {
		mPackageName = applicationInfo.packageName;
		mIcon = applicationInfo.loadIcon(packageManager);
		try {
			mName = applicationInfo.loadLabel(packageManager).toString();
		} catch (Exception e) {
			mName = mPackageName;
		}
	}


	@NonNull
	public String getName() {
		if (mName != null)
			return mName;
		return ("");
	}

	public Drawable getIcon() {
		return mIcon;
	}

	public String getPackageName() {
		return mPackageName;
	}
}
