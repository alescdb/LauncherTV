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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkCapabilities;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.support.annotation.RequiresApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.View;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Network extends BroadcastReceiver {
	public enum State { NONE, WIFI, ETHERNET, OTHER, UNKNOWN }

	private final ImageView mIcon;
	private static final int REFRESH_DELAY = 5000;
	private final Context mContext;
	private final TextView mView;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private boolean mRunning;
	private boolean mRegistered;
	private AlertDialog mDialog;
	private final Runnable mRefresh = new Runnable() {
		@Override
		public void run() {
			if (mRunning)
				refresh();
		}
	};

	public Network(Context context, TextView view, ImageView icon) {
		mIcon = icon;
		mContext = context.getApplicationContext();
		mView = view;
	}

	public void start() {
		if (mRunning)
			return;
		mRunning = true;
		try {
			IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				mContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
			else
				mContext.registerReceiver(this, filter);
			mRegistered = true;
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		refresh();
	}

	public void stop() {
		mRunning = false;
		mHandler.removeCallbacks(mRefresh);
		if (mRegistered) {
			mContext.unregisterReceiver(this);
			mRegistered = false;
		}
		if (mDialog != null) {
			mDialog.dismiss();
			mDialog = null;
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (mRunning)
			refresh();
	}

	public void showDetails() {
		mDialog = new AlertDialog.Builder(mView.getContext()).setTitle(R.string.ip_addresses)
				.setMessage(mView.getText()).setPositiveButton(android.R.string.ok, null).show();
	}

	private void refresh() {
		if (mIcon.getVisibility() == View.VISIBLE)
			setState(getState(mContext));
		String text = mView.getVisibility() == View.VISIBLE ? read(mContext) : "";
		if (!TextUtils.equals(mView.getText(), text)) {
			mView.setText(text);
			if (mDialog != null && mDialog.isShowing())
				mDialog.setMessage(text);
		}
		mHandler.removeCallbacks(mRefresh);
		mHandler.postDelayed(mRefresh, REFRESH_DELAY);
	}

	private void setState(State state) {
		int icon;
		int label;
		switch (state) {
			case WIFI: icon = R.drawable.ic_network_wifi; label = R.string.network_wifi; break;
			case ETHERNET: icon = R.drawable.ic_network_ethernet; label = R.string.network_ethernet; break;
			case OTHER: icon = R.drawable.ic_network_other; label = R.string.network_other; break;
			case NONE: icon = R.drawable.ic_network_off; label = R.string.network_disconnected; break;
			default: icon = R.drawable.ic_network_off; label = R.string.network_unavailable; break;
		}
		mIcon.setImageResource(icon);
		mIcon.setContentDescription(mContext.getString(label));
	}

	public static State getState(Context context) {
		try {
			ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (manager == null)
				return (State.UNKNOWN);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				return (Connections.getState(manager));
			NetworkInfo active = manager.getActiveNetworkInfo();
			if (active != null && active.isConnected())
				return (getType(active));
			State state = State.NONE;
			for (NetworkInfo network : manager.getAllNetworkInfo()) {
				if (network == null || !network.isConnected())
					continue;
				State type = getType(network);
				if (type == State.ETHERNET)
					return (type);
				if (state != State.WIFI)
					state = type;
			}
			return (state);
		} catch (Exception e) {
			e.printStackTrace();
			return (State.UNKNOWN);
		}
	}

	private static State getType(NetworkInfo network) {
		if (network.getType() == ConnectivityManager.TYPE_ETHERNET)
			return (State.ETHERNET);
		if (network.getType() == ConnectivityManager.TYPE_WIFI)
			return (State.WIFI);
		return (State.OTHER);
	}

	public static String read(Context context) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				return (Connections.read(context));
			if (!isConnected(context))
				return (context.getString(R.string.network_disconnected));
			List<String> rows = new ArrayList<>();
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces != null && interfaces.hasMoreElements()) {
				NetworkInterface network = interfaces.nextElement();
				if (!network.isUp() || network.isLoopback())
					continue;
				List<String> addresses = getAddresses(network);
				if (!addresses.isEmpty())
					rows.add(network.getName() + ": " + TextUtils.join(", ", addresses));
			}
			return (formatRows(context, rows));
		} catch (Exception e) {
			e.printStackTrace();
			return (context.getString(R.string.network_unavailable));
		}
	}

	private static boolean isConnected(Context context) {
		ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager != null) {
			for (NetworkInfo network : manager.getAllNetworkInfo()) {
				if (network != null && network.isConnected())
					return (true);
			}
		}
		return (false);
	}

	private static List<String> getAddresses(NetworkInterface network) {
		List<String> addresses = new ArrayList<>();
		Enumeration<InetAddress> values = network.getInetAddresses();
		while (values.hasMoreElements()) {
			InetAddress address = values.nextElement();
			addAddress(addresses, address);
		}
		Collections.sort(addresses);
		return (addresses);
	}

	private static void addAddress(List<String> addresses, InetAddress address) {
		if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isMulticastAddress()) {
			String value = address.getHostAddress();
			if (!addresses.contains(value))
				addresses.add(value);
		}
	}

	private static String formatRows(Context context, List<String> rows) {
		Collections.sort(rows);
		return (rows.isEmpty() ? context.getString(R.string.ip_unavailable) : TextUtils.join("\n", rows));
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static class Connections {
		static State getState(ConnectivityManager manager) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				NetworkCapabilities active = manager.getNetworkCapabilities(manager.getActiveNetwork());
				if (active != null)
					return (getType(active));
			} else {
				NetworkInfo active = manager.getActiveNetworkInfo();
				if (active != null && active.isConnected())
					return (Network.getType(active));
			}
			State state = State.NONE;
			for (android.net.Network network : manager.getAllNetworks()) {
				NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
				if (capabilities == null)
					continue;
				State type = getType(capabilities);
				if (type == State.ETHERNET)
					return (type);
				if (state != State.WIFI)
					state = type;
			}
			return (state);
		}

		private static State getType(NetworkCapabilities capabilities) {
			if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
				return (State.ETHERNET);
			if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
				return (State.WIFI);
			return (State.OTHER);
		}

		static String read(Context context) {
			ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (manager == null)
				return (context.getString(R.string.network_unavailable));
			boolean connected = false;
			Map<String, List<String>> interfaces = new TreeMap<>();
			for (android.net.Network network : manager.getAllNetworks()) {
				if (manager.getNetworkCapabilities(network) == null)
					continue;
				connected = true;
				LinkProperties properties = manager.getLinkProperties(network);
				if (properties == null || TextUtils.isEmpty(properties.getInterfaceName()))
					continue;
				String name = properties.getInterfaceName();
				List<String> addresses = interfaces.get(name);
				if (addresses == null) {
					addresses = new ArrayList<>();
					interfaces.put(name, addresses);
				}
				for (LinkAddress address : properties.getLinkAddresses())
					addAddress(addresses, address.getAddress());
			}
			if (!connected)
				return (context.getString(R.string.network_disconnected));
			List<String> rows = new ArrayList<>();
			for (Map.Entry<String, List<String>> entry : interfaces.entrySet()) {
				if (!entry.getValue().isEmpty()) {
					Collections.sort(entry.getValue());
					rows.add(entry.getKey() + ": " + TextUtils.join(", ", entry.getValue()));
				}
			}
			return (formatRows(context, rows));
		}
	}

}
