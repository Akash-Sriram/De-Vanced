# De-Vanced (Google Photos)

[![Build](https://github.com/Akash-Sriram/De-Vanced/actions/workflows/release.yml/badge.svg)](https://github.com/Akash-Sriram/De-Vanced/actions)
[![Latest Release](https://img.shields.io/github/v/release/Akash-Sriram/GooglePhotos-Patched?label=Prebuilt%20APK&color=blue)](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases/latest)
[![License](https://img.shields.io/github/license/Akash-Sriram/De-Vanced)](LICENSE)

Modular Morphe patches for Google Photos with standalone GmsCore compatibility, Pixel feature spoofing, and dynamic Phenotype flag management.

---

## ✨ Features

| Patch | Description |
|---|---|
| **Account avatar** | Standalone profile photo bridge for MicroG/GmsCore with RAM caching and 60fps animations across Toolbar, Bento, and Account Switchers. |
| **GmsCore support** | Signature verification and service checks bypass for non-root Google account login and persistent sessions. |
| **Spoof features** | Unlocks unlimited original-quality cloud backup (Pixel XL profile) and Pixel editing tools (Magic Eraser, Portrait Blur, Sky, Unblur, HDR). |
| **Enable DCIM folders backup control** | Prevents non-camera folders (Screenshots, WhatsApp) from being forced into Camera backup. |
| **Enable Phenotype flag manager** | In-app real-time flag debugger in `Settings > 🛠️ Morphe Flags` with import/export, batch editing, and auto-seeded UI presets. |

---

## 🛠️ Pre-Seeded UI Flags

The following phenotype flags are automatically enabled on fresh launch and restorable via **⚡ Reset Defaults**:

| Flag ID | Type | Default | Description |
|---|:---:|:---:|---|
| `2675` | Boolean | `true` | Modern UI layout components |
| `2892` | Boolean | `true` | Memories stories feature |
| `3013` | Long | `1` | Gemini / Ask Photos bottom tab |
| `3023` | Boolean | `true` | Enhanced Memories navigation |
| `3024` | Boolean | `true` | Floating pill bottom navigation bar |
| `3026` | Boolean | `true` | Redesigned Memories carousel |
| `3606` | Boolean | `true` | Dynamic layout controls |
| `3611` | Boolean | `true` | Modern card styling |
| `4306` | Boolean | `true` | Dynamic top action bar |
| `4311` | Boolean | `true` | Floating navigation elevation |
| `45732792` | Boolean | `true` | Updated grid layout renderer |
| `45743215` | Boolean | `true` | Floating date capsule pill `[ Today ]` |
| `45762698` | Long | `2` | Collections Shelves V2 layout |
| `45802110` | Long | `2` | Collections Shelves V2 content view |

---

## 🔨 Building from Source

### 1. Compile Patches Bundle (.mpp)
```bash
git clone https://github.com/Akash-Sriram/De-Vanced.git
cd De-Vanced
./gradlew :patches:buildAndroid
```
Compiled bundle will be in `patches/build/libs/patches-*.mpp`.

### 2. Patch Google Photos with Morphe CLI
```bash
java -jar morphe-cli.jar patch \
  --patches=patches/build/libs/patches-1.10.0.mpp \
  --out=GooglePhotos-Patched.apk \
  GooglePhotos-Base.apk
```

---

## 📦 Downloads

- **Pre-built APKs**: [Akash-Sriram/GooglePhotos-Patched/releases](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)
- **MicroG / GmsCore**: [ReVanced GmsCore](https://github.com/ReVanced/GmsCore/releases)

---

## 📜 Credits & License

- Based on [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced) and [Morphe](https://github.com/MorpheApp).
- Licensed under [GNU General Public License v3.0](LICENSE).

