# Wallpaper

**Show wallpaper on home** displays the wallpaper and is enabled by default.
Disable it for a black background without changing the system image.

## Choosing a wallpaper

Most Android TV devices have no wallpaper picker, so LauncherTV includes its
own. Open **Wallpaper** in preferences and use the remote to browse:

- **Built-in**: the wallpaper shipped with LauncherTV, always available.
- **Show local and USB images**: asks for photo access, then lists images
  indexed by Android on internal storage, SD cards and USB drives, newest
  first (up to 500). If access was refused permanently, the tile opens the
  app's Android settings.
- **Other apps…**: shown only when an app can pick images on this device.
- **Android wallpaper**: shown only when the device has a system wallpaper
  picker. LauncherTV then shows the Android wallpaper again.

The focused image is previewed behind the list. Press OK to apply it.
LauncherTV keeps a private copy (up to 32 MB) without changing Android's
wallpaper. Photo orientation is respected, and an invalid image keeps the
previous wallpaper. The wallpaper is also shown, slightly dimmed, behind the
application list and folders.

In **Wallpaper scaling**, choose:

- **Whole image**: show every edge, adding black borders when needed.
- **Fill screen**: crop the center to fill the screen without distortion.

## Default wallpaper

On devices without a usable Android wallpaper (no wallpaper support, or a TV
without a wallpaper picker), LauncherTV uses the built-in wallpaper on first
launch after a new installation instead of a black home screen. Updates keep
the current background.

**Restore default wallpaper** asks for confirmation, then removes the chosen
LauncherTV wallpaper. On devices without a usable Android wallpaper, the
built-in wallpaper is used again. Otherwise the Android home wallpaper is
restored for all launchers; a separate lock screen wallpaper is preserved on
Android 7 and later.
