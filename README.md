# Google Photos Patches

A modular patch suite for **Google Photos** built on the [Morphe Patcher](https://github.com/MorpheApp/morphe-patcher), forked from [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced) with enhanced UI customization, built-in photo editor support, and automated releases.

---

## ✨ Features

| Feature | Description |
|---|---|
| **Built-in Photo Editor** | Full native Google Photos editor suite (**Enhance, Dynamic, Boost, Auto, Crop, Adjust, Actions, Markup**) without external app dependencies. |
| **Unlimited Backup** | Spoofs Google Pixel devices to unlock **unlimited original-quality photo & video backups** and Pixel-exclusive editing tools. |
| **In-App Phenotype Flag Manager** | Real-time feature flag editor accessible directly from **Settings > Morphe Flags**. Search, edit, add, delete, import, export, and reset flags on the fly. |
| **Modern UI & Navigation** | Out-of-the-box support for the **Floating Date Capsule Pill** (`[ Today ]`), **Redesigned Collections Shelves V2**, **Floating Bottom Navigation Bar**, **Gemini AI / Ask Photos Tab**, and **Memories Stories**. |
| **MicroG / GmsCore Support** | Runs seamlessly on non-rooted devices using MicroG / GmsCore for Google account authentication. |
| **Account Avatar Bridge** | Loads your Google profile picture across the top toolbar, account sheet, and account switcher. |
| **Account Persistence** | Keeps your selected Google account logged in reliably across app restarts. |
| **DCIM Backup Control** | Gives granular control over individual camera and gallery subfolders rather than forcing entire `DCIM` directory sync. |

---

## 🛠️ Included Default Phenotype Presets

On fresh installs, the patch automatically pre-configures verified modern UI flags:

```json
{
  "2675": true,
  "2892": true,
  "3013": 1,
  "3023": true,
  "3024": true,
  "3026": true,
  "3606": true,
  "3611": true,
  "4306": true,
  "4311": true,
  "45732792": true,
  "45743215": true,
  "45762698": 2,
  "45802110": 2
}
```

---

## 🏗️ Building Locally

### Prerequisites
- **JDK 17 or 21**
- **Android SDK** (Command-line Tools & Build Tools 35.0.0+)
- **Gradle** (or use bundled `./gradlew`)

### Build the Patch Bundle (`.mpp`)
```bash
./gradlew :extensions:shared:library:assembleRelease :patches:buildAndroid
```
The compiled patch bundle will be located at:
`patches/build/libs/patches-<version>.mpp`

### Patching with Morphe CLI
```bash
java -jar morphe-desktop-all.jar patch -p patches/build/libs/patches-<version>.mpp -o GooglePhotos-Patched.apk <path-to-target-google-photos.apk>
```

---

## 📦 Automated Pre-Built Releases

Looking for ready-to-install APKs? Check out our companion automated release repository:
👉 **[Akash-Sriram/GooglePhotos-Patched](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)**

---

## 📜 Credits & License
- Forked from [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced)
- Powered by [Morphe Patcher](https://github.com/MorpheApp) & [ReVanced](https://github.com/ReVanced)
- Licensed under the [GNU General Public License v3.0](LICENSE)
