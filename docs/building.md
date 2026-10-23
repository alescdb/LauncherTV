# Building

Use JDK 25, Android SDK Platform 37, Build Tools 37.0.0 and the Gradle wrapper.
Set `ANDROID_HOME` or add the SDK path to `local.properties`:

```properties
sdk.dir=/opt/android-sdk
```

```sh
./gradlew --no-daemon assembleDebug assembleRelease lintDebug checkstyle
```

APKs are written under `launcher/build/outputs/apk/`. Release APKs are unsigned
unless private signing properties are configured. Preserve the original signing
key when distributing updates.

The project compiles and targets API 37, with minimum API 14 and Java 8 bytecode.
Keep dependencies and newer API calls compatible with older devices.

## Default tiles for custom ROMs

Populate `default_applications` in
`launcher/src/main/res/values/notrans.xml`, or override it in a ROM resource
variant. Package names fill the initial grid in row order; empty entries leave
a tile blank.

```xml
<string-array name="default_applications" translatable="false">
    <item>com.android.settings</item>
    <item></item>
</string-array>
```

The stock array is empty. Defaults apply once to a fresh installation and never
overwrite an existing layout. Unavailable packages retain their assignments.
Removing a default tile does not make it return after a restart or update.
