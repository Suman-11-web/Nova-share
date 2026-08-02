# ✦ Nova Share

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub Actions CI](https://github.com/nova-share/nova-share-android/actions/workflows/ci.yml/badge.svg)](.github/workflows/ci.yml)
[![Android MinSDK](https://img.shields.io/badge/MinSDK-24-emerald.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](app/build.gradle.kts)

**Nova Share** is a modern, ultra-fast, secure, privacy-focused open-source Android file sharing application. It enables high-speed cross-platform file transfers between Android devices, PCs, Macs, Linux workstations, and web browsers over local Wi-Fi and Wi-Fi Direct without internet access or advertisements.

---

## 🚀 Key Features

- ✦ **Cross-Platform Web Portal (Phone ↔ PC)**: Built-in HTTP server lets any desktop browser (Windows, macOS, Linux) download or upload files to your phone instantly by scanning a QR code.
- ⚡ **Ultra-Fast Local P2P Sharing**: Transfer files at full Wi-Fi network speeds with live transfer progress, real-time bandwidth meter, and ETA calculation.
- 🔒 **AES-256 Encryption & PIN Pairing**: End-to-end encrypted transfer chunks and secure QR / PIN authentication.
- 📁 **Universal File Format Support**: Photos, 4K Videos, Audio tracks, Documents, PDFs, APKs, Archives (ZIP/RAR), Contacts (VCF), and Entire Folders.
- 📊 **Storage Analytics & History**: Detailed transfer logs, SHA-256 checksum verification, and storage category breakdown gauge.
- 🎨 **Material 3 & Motion**: Edge-to-edge layout, dynamic Material You color schemes, AMOLED pitch black theme, and sleek entrance animations.
- 🤖 **Complete GitHub Actions CI/CD**: Automated unit testing, linting, build verification, and release packaging.

---

## 🛠 Tech Stack & Architecture

Nova Share is built strictly following Android MVVM and Clean Architecture guidelines:

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Local Persistence**: Room Database
- **Preferences**: DataStore Preferences
- **Asynchrony**: Kotlin Coroutines & Flow
- **Networking**: Java Socket P2P & Embedded Java HttpServer
- **CI/CD**: GitHub Actions (Java 17 / 21)

---

## 📄 License

Nova Share is released under the [MIT License](LICENSE).
