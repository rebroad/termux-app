# Local rebroad APK signing

This is fork-local build information. Do not copy the signing files into this
repository or commit passwords, generated APKs, or build outputs.

The Termux release keystore is stored outside the repositories at:

```text
/home/rebroad/.config/rebroad-termux/termux-release.jks
/home/rebroad/.config/rebroad-termux/termux-release.pass
```

The password file must remain mode `600`. The keystore alias is
`rebroad-termux`.

From the source checkout, synchronize first and build only in the designated
external build tree:

```sh
cpto /mnt/kingston/@home/rebroad/src/termux-app \
    /mnt/kingston/builds/rebroad/src/termux-app.build

pass=$(< /home/rebroad/.config/rebroad-termux/termux-release.pass)
TERMUX_REBROAD_SIGNING_STORE_FILE=/home/rebroad/.config/rebroad-termux/termux-release.jks \
TERMUX_REBROAD_SIGNING_STORE_PASSWORD="$pass" \
TERMUX_REBROAD_SIGNING_KEY_ALIAS=rebroad-termux \
TERMUX_REBROAD_SIGNING_KEY_PASSWORD="$pass" \
/mnt/kingston/builds/rebroad/src/termux-app.build/gradlew \
    -p /mnt/kingston/builds/rebroad/src/termux-app.build :app:assembleRelease
unset pass
```

Before installing, verify that the APK version code is higher than the
installed package. Install it as an update with ADB:

```sh
adb install -r app/build/outputs/apk/release/termux-app_apt-android-7-release_universal.apk
```

An update must retain package name `com.termux`, use the rebroad keystore, and
have a higher version code. Never uninstall the existing app merely to bypass
a signing or version-code error; that can remove its private Termux data.
