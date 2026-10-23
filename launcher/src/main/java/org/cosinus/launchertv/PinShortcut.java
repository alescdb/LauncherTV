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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.O)
public class PinShortcut {
	public static AppInfo get(Context context, Intent intent) {
		LauncherApps.PinItemRequest request = getRequest(context, intent);
		if (request == null)
			return (null);
		ShortcutInfo info = request.getShortcutInfo();
		Drawable icon = getIcon(context, info);
		return (new AppInfo(null, info.getShortLabel().toString(), icon));
	}

	public static boolean accept(Context context, Intent intent) throws Exception {
		LauncherApps.PinItemRequest request = getRequest(context, intent);
		if (request == null)
			return (false);
		ShortcutInfo info = request.getShortcutInfo();
		Drawable icon = getIcon(context, info);
		UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
		long user = users.getSerialNumberForUser(info.getUserHandle());
		if (!request.accept())
			return (false);
		return (new Shortcuts(context).addPinned(info.getShortLabel().toString(), icon, info.getPackage(), info.getId(), user));
	}

	public static void launch(Context context, String packageName, String id, long user) {
		UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
		UserHandle profile = users.getUserForSerialNumber(user);
		if (profile == null)
			throw new ActivityNotFoundException();
		LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		apps.startShortcut(packageName, id, null, null, profile);
	}

	private static LauncherApps.PinItemRequest getRequest(Context context, Intent intent) {
		LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		LauncherApps.PinItemRequest request = apps.getPinItemRequest(intent);
		if (request == null || !request.isValid() || request.getRequestType() != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT)
			return (null);
		return (request);
	}

	private static Drawable getIcon(Context context, ShortcutInfo info) {
		LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		Drawable icon = apps.getShortcutIconDrawable(info, context.getResources().getDisplayMetrics().densityDpi);
		return (icon == null ? context.getPackageManager().getDefaultActivityIcon() : icon);
	}
}
