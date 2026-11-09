<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/numio-wordmark-dark.png" />
    <img src="docs/numio-wordmark-light.png" width="220" alt="Numio." />
  </picture>
</p>

<p align="center"><img src="docs/files-icon.png" width="120" alt="Files icon" /></p>

<h1 align="center">Files</h1>

<p align="center">
  <b>A calm, private file manager for Android.</b><br/>
  Find, copy, move, share and tidy everything on your phone.<br/>
  No ads. No tracking. No internet. Just your files.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License: GPL-3.0" />
  <img src="https://img.shields.io/badge/Kotlin-100%25-purple" alt="Kotlin 100%" />
  <img src="https://img.shields.io/badge/Min%20SDK-26-orange" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Internet-none-brightgreen" alt="Internet: none" />
  <img src="https://img.shields.io/badge/Ads-none-brightgreen" alt="Ads: none" />
</p>

Files is a free, open-source file manager for Android.
Part of [Numio](https://getnumio.org) — small, honest apps made by a Keralite.

> 🚧 **In testing.** Not released yet.

---

## 📁 Why Files

- ✅ **No internet permission at all.** Your files never leave your phone. Every build checks this.
- ✅ **No ads, no accounts, no "cloud".**
- ✅ **Free and open source** (GPL-3.0). Anyone can read the code.

## ✨ Features

### 🏠 Home
- ✅ Storage at a glance: used, free, and what's taking the space
- ✅ SD cards and USB drives, when plugged in
- ✅ Quick tiles: Images, Videos, Audio, Docs, Downloads, APKs, Archives, Trash
- ✅ Recent files, with previews

### 🗂️ Browse
- ✅ Every folder, with a tappable path; jump between Internal, SD card and USB
- ✅ List or grid view, per folder (photo folders open as a grid)
- ✅ Previews: photos, video frames, APK icons, album art, PDF and Illustrator pages, Photoshop pictures
- ✅ Sort by name, newest or largest — folders always first
- ✅ Search every folder by name, with recent searches

### ✂️ Do things with files
- ✅ Long-press to select, then **Copy, Move, Share, Rename, Zip or Delete**
- ✅ Progress with **Cancel** for big jobs; nothing is ever left half-done
- ✅ **Details**: size, dates, path; camera info and GPS warning for photos; app info for APKs
- ✅ **Trash** — deleted things wait 30 days so you can restore them
- ✅ **Starred** — your favourite files and folders one tap away

### 📖 Opens files right inside the app
- ✅ **ZIP**: look inside, extract, make your own
- ✅ **Text, Markdown, CSV, JSON, HTML, code** — with editing for text files
- ✅ **Word (.docx) and Excel (.xlsx)** reader
- ✅ **Apple Pages, Numbers, Keynote** — from the preview Apple saves inside
- ✅ **Illustrator (.ai)** — opens as PDF, with **Save as PDF**
- ✅ **Photoshop (.psd)** — the flat picture, with **Save as PNG**

### 🧹 Clean up
- ✅ **Empty folders** — only truly empty ones; hidden files count, Android/ is never touched
- ✅ **Duplicates** — same content checked by fingerprint, not just by name
- ✅ **Large files** and **APK files**, to Trash

### 🎨 Numio look
- ✅ Dark, light, or follow the phone
- ✅ The Numio amber, everywhere it counts

## 🔐 Permissions

| Permission | Why |
|---|---|
| All files access | A file manager has to see every folder. Android asks you first. |
| Install apps | Only so tapping an `.apk` hands it to Android's installer. Android asks you again before anything installs. |
| Internet | **None.** |

## 🛠️ Built with

- Kotlin + Jetpack Compose
- Android's own file index (MediaStore) for the Home tiles
- [Inter](https://rsms.me/inter/) font (SIL OFL 1.1, see `licenses/`)

Test APKs are built by GitHub Actions on every pull request. Real releases are
built and signed by hand, so the signing key never goes to GitHub.

## 📜 License

GPL-3.0. See [LICENSE](LICENSE).

## 💛 Credits

Built by RR with help from Claude.
