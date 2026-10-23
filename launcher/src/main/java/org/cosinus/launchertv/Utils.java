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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.TypedValue;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

public class Utils {
	public static List<AppInfo> loadApplications(Context context) {
		return (loadApplications(context, false));
	}

	public static List<AppInfo> loadApplications(Context context, boolean includeHidden) {
		PackageManager packageManager = context.getPackageManager();
		
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_HOME);
		List<ResolveInfo> intentActivities = packageManager.queryIntentActivities(mainIntent, 0);
		List<ResolveInfo> fallbackHomes = new ArrayList<>();
		for (ResolveInfo activity : intentActivities) {
			if (activity.priority < 0)
				fallbackHomes.add(activity);
		}
		intentActivities.removeAll(fallbackHomes);
		
		mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		intentActivities.addAll(packageManager.queryIntentActivities(mainIntent, 0));

		mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
		intentActivities.addAll(packageManager.queryIntentActivities(mainIntent, 0));
				
		intentActivities.addAll(fallbackHomes);
		Set<String> knownPackages = new HashSet<>();
		List<AppInfo> entries = new ArrayList<>();

		if (intentActivities != null) {
			for (ResolveInfo resolveInfo : intentActivities) {
				if (!context.getPackageName().equals(resolveInfo.activityInfo.packageName) && 
					!knownPackages.contains(resolveInfo.activityInfo.packageName)) {
					entries.add(new AppInfo(packageManager, resolveInfo));
					knownPackages.add(resolveInfo.activityInfo.packageName);
				}
			}
		}

		entries.addAll(new Shortcuts(context).load());
		entries.addAll(new Folders(context).load());
		if (!includeHidden) {
			Protection protection = new Protection(context);
			Iterator<AppInfo> items = entries.iterator();
			while (items.hasNext()) {
				if (protection.isHidden(items.next().getId()))
					items.remove();
			}
		}
		Collections.sort(entries, new Comparator<AppInfo>() {
			@Override
			public int compare(AppInfo lhs, AppInfo rhs) {
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
		return entries;
	}

	public static Intent getLaunchIntentForPackage(Context context, String packageName) {
		PackageManager pm = context.getPackageManager();
		Intent launchIntent = getHomeIntent(context, packageName, false);
		if (launchIntent == null)
			launchIntent = pm.getLaunchIntentForPackage(packageName);

		if (launchIntent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName);
		}

		if (launchIntent == null)
			launchIntent = getHomeIntent(context, packageName, true);
		return launchIntent;
	}

	private static Intent getHomeIntent(Context context, String packageName, boolean fallback) {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.setPackage(packageName);
		List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(intent, 0);
		for (ResolveInfo activity : activities) {
			if (fallback || activity.priority >= 0) {
				intent.setClassName(activity.activityInfo.packageName, activity.activityInfo.name);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
				return (intent);
			}
		}
		return (null);
	}

	public static int getPixelFromDp(Context context, int dp) {
		Resources r = context.getResources();
		return ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
	}
}
