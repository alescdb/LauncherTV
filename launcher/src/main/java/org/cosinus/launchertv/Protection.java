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
import android.content.SharedPreferences;
import android.util.Base64;

import org.cosinus.launchertv.activities.Unlock;
import org.cosinus.launchertv.activities.ApplicationList;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Protection {
	public static final int MIN_LENGTH = 4;
	public static final int MAX_LENGTH = 8;
	public static final String HIDDEN = "hidden";
	public static final String LOCKED = "locked";
	private static final int ROUNDS = 65536;
	private static final int BITS = 256;
	private static final int MAX_ATTEMPTS = 5;
	private static final long RETRY_DELAY = 30000;
	private static final String ATTEMPTS = "attempts";
	private static final String RETRY_AT = "retry_at";
	private static final String HASH = "hash";
	private static final String SALT = "salt";
	private final Context mContext;
	private final SharedPreferences mPreferences;

	public Protection(Context context) {
		mContext = context.getApplicationContext();
		mPreferences = mContext.getSharedPreferences("protection", Context.MODE_PRIVATE);
	}

	public boolean isEnabled() {
		return (mPreferences.contains(HASH));
	}

	public void setPin(char[] pin) throws Exception {
		if (pin == null || pin.length < MIN_LENGTH || pin.length > MAX_LENGTH)
			throw new IllegalArgumentException("Invalid PIN format");
		for (char digit : pin) {
			if (digit < '0' || digit > '9')
				throw new IllegalArgumentException("Invalid PIN format");
		}
		byte[] salt = new byte[16];
		// Éviter le générateur mal initialisé de certaines versions Android 4.3 et antérieures.
		try (DataInputStream random = new DataInputStream(new FileInputStream("/dev/urandom"))) {
			random.readFully(salt);
		}
		byte[] hash = derive(pin, salt);
		try {
			if (!mPreferences.edit().putString(SALT, encode(salt)).putString(HASH, encode(hash))
					.remove(ATTEMPTS).remove(RETRY_AT).commit())
				throw new java.io.IOException("Unable to save protection");
		} finally {
			Arrays.fill(hash, (byte) 0);
		}
	}

	public boolean verify(char[] pin) throws Exception {
		if (!isEnabled() || getRetryDelay() > 0)
			return (false);
		byte[] hash = derive(pin, Base64.decode(mPreferences.getString(SALT, ""), Base64.NO_WRAP));
		boolean valid;
		try {
			valid = MessageDigest.isEqual(hash, Base64.decode(mPreferences.getString(HASH, ""), Base64.NO_WRAP));
		} finally {
			Arrays.fill(hash, (byte) 0);
		}
		int attempts = valid ? 0 : mPreferences.getInt(ATTEMPTS, 0) + 1;
		SharedPreferences.Editor editor = mPreferences.edit().putInt(ATTEMPTS, attempts);
		if (valid)
			editor.remove(RETRY_AT);
		else if (attempts >= MAX_ATTEMPTS)
			editor.putLong(RETRY_AT, System.currentTimeMillis() + RETRY_DELAY).putInt(ATTEMPTS, 0);
		editor.commit();
		return (valid);
	}

	public long getRetryDelay() {
		long delay = mPreferences.getLong(RETRY_AT, 0) - System.currentTimeMillis();
		if (delay > RETRY_DELAY) {
			// Une remise à l’heure ne doit pas prolonger le blocage.
			mPreferences.edit().putLong(RETRY_AT, System.currentTimeMillis() + RETRY_DELAY).commit();
			return (RETRY_DELAY);
		}
		return (Math.max(0, delay));
	}

	public boolean disable() {
		return (mPreferences.edit().remove(HASH).remove(SALT).remove(ATTEMPTS).remove(RETRY_AT).commit());
	}

	public Set<String> getItems(String key) {
		return (new HashSet<>(mPreferences.getStringSet(key, new HashSet<String>())));
	}

	public boolean setItems(String key, Set<String> ids) {
		return (mPreferences.edit().putStringSet(key, new HashSet<>(ids)).commit());
	}

	public boolean isHidden(String id) {
		return (isEnabled() && matches(HIDDEN, id));
	}

	public boolean isLocked(String id) {
		return (isEnabled() && (matches(LOCKED, id) || matches(HIDDEN, id)));
	}

	private boolean matches(String key, String id) {
		Set<String> ids = getItems(key);
		if (ids.contains(id))
			return (true);
		String packageName = new Shortcuts(mContext).getPackage(id);
		return (packageName != null && ids.contains(packageName));
	}

	public static Intent getIntent(Context context, String id) {
		Intent intent = new Intent(context, Unlock.class);
		intent.putExtra(ApplicationList.APP_ID, id);
		return (intent);
	}

	private byte[] derive(char[] pin, byte[] salt) throws Exception {
		PBEKeySpec spec = new PBEKeySpec(pin, salt, ROUNDS, BITS);
		try {
			return (SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded());
		} finally {
			spec.clearPassword();
		}
	}

	private String encode(byte[] value) {
		return (Base64.encodeToString(value, Base64.NO_WRAP));
	}
}
