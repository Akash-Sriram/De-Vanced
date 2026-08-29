# Morphe Google Photos Patches

Enhanced, standalone [Morphe](https://github.com/MorpheApp) patch bundle for **Google Photos**, providing non-root GmsCore/MicroG support, Pixel-exclusive feature spoofing (unlimited backup), full OneGoogle avatar integration, and modern UI enhancements.

---

## ✨ Features & Included Patches

### 1. 🛡️ GmsCore Support & MicroG Authentication
- **Non-Root Operation**: Repackages and redirects Google Play Services dependencies to GmsCore / MicroG (`app.revanced.android.gms`).
- **Signature Bypass**: Safely bypasses internal Google Play Services signature checks to keep Maps timeline, account initialization, and partner sharing fully operational.
- **Account Persistence**: Fixes account persistence across cold restarts when running on MicroG.

### 2. 👤 OneGoogle Profile Avatar Bridge
- **Authenticated Profile Images**: Direct OAuth `openid` token-backed avatar engine that fetches and caches official Google profile pictures.
- **Full UI Binding**: Automatically binds circular anti-aliased avatars across:
  - Top Toolbar Account Disc (`og_apd_internal_image_view`)
  - Bento Account Management Sheet (`og_bento_selected_account_avatar`)
  - Account Switcher & Available Accounts List (`og_bento_available_account_avatar`)
- **Signed-Out State Handling**: Automatically detects "Use without an account" mode and seamlessly restores native generic person placeholders.

### 3. ♾️ Unlimited Cloud Backup & Pixel Features
- **Device Spoofing**: Spoofs device build parameters to **Google Pixel XL** (`marlin`) to enable lifetime unlimited Google Photos cloud storage backup.
- **Pixel-Exclusive Features**: Activates Google Pixel editing capabilities and features across multi-DEX without breaking device compatibility.

### 4. 🎨 Modern Floating Pill UI & Phenotype Engine
- **Phenotype Flags Seeding**: Embeds official Google Photos Phenotype XML configuration to activate the modern floating pill navigation bar (`[ Photos | Collections | Create ]` + `🔍`).
- **Startup Injection**: Auto-seeds configuration flags into in-memory Phenotype caches on initial launch.

### 5. 📁 Granular DCIM Backup Control
- **Decoupled Folder Sync**: Removes the forced always-on backup rule for the root `DCIM/` folder.
- **Individual Folder Control**: Allows independent backup toggles for subdirectories such as `DCIM/Camera`, `DCIM/Screenshots`, and custom folders.

---

## 📦 Building the Patch Bundle

### Prerequisites
- JDK 17 or higher
- Android SDK Build Tools (API 34+)

### Build Command
To compile the extension runtime and build the standalone `.mpp` patch bundle:

```bash
# Build shared extension DEX and Android MPP bundle
./gradlew :extensions:shared:library:assembleRelease :patches:buildAndroid
```

The compiled patch bundle will be located at:
`patches/build/libs/patches-<version>.mpp`

---

## 🚀 How to Use

### Option A: Using Morphe Manager (Android)
1. Download or push `patches-<version>.mpp` to your Android device (e.g. `Download/` folder).
2. Open **Morphe Manager** on your device.
3. Navigate to **Settings > Sources > Local Patches** and select the `.mpp` file.
4. Select stock **Google Photos** (`com.google.android.apps.photos`) and apply patches.

### Option B: Using Morphe Desktop CLI
```bash
java -jar morphe-desktop.jar \
  -p patches-<version>.mpp \
  -a com.google.android.apps.photos.apk \
  -o GooglePhotos-Patched.apk \
  --keystore ~/.android/debug.keystore
```

---

## 🛠️ Patch Configuration

All Google Photos patches are enabled by default:

| Patch Name | Type | Default | Description |
|---|---|:---:|---|
| **GmsCore support** | Bytecode & Resource | `true` | Enables non-root operation with MicroG / GmsCore |
| **Fix selected account persistence** | Bytecode | `true` | Prevents account reset after restart |
| **Spoof features** | Bytecode | `true` | Enables Pixel experience & unlimited backup |
| **Phenotype assets** | Resource | `true` | Injects modern floating pill UI flags |
| **Enable DCIM folders backup control** | Bytecode | `true` | Enables granular per-subfolder backup toggles |

---

## 📄 License

Licensed under the [GNU General Public License v3.0](LICENSE).
