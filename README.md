# 🚀 EvolveLauncher - VR Launcher for Meta Quest

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/JarJarBlinkz/EvolveLauncher)
[![Platform](https://img.shields.io/badge/platform-Meta%20Quest-red.svg)](https://www.meta.com/quest/)

**EvolveLauncher** is a customizable home launcher for Meta Quest headsets. Organize your apps, track playtime, and personalize your VR experience.

---

## ✨ Current Features

### 📱 App Launcher

<img width="3710" height="1895" alt="main" src="https://github.com/user-attachments/assets/a9acac6f-bdba-496e-9a4d-2d77f30fe1d9" /> <img width="3728" height="1953" alt="settings" src="https://github.com/user-attachments/assets/9da831aa-32f7-465d-93bf-448610b19cec" />

- Customizable app grid with adjustable icon sizes
- Real-time search filtering by app name or package
- Launch any installed app with one tap
- App hiding (remove unwanted apps from view)

### 📁 Category Management
- Create, rename, and delete categories
- Move multiple apps to categories at once (Edit Mode)
- Remove apps from categories
- Category badges displayed on app icons
- Category bar for quick switching

<img width="3733" height="1954" alt="playtime stats" src="https://github.com/user-attachments/assets/497a910b-965e-4014-b9fd-537459607f65" />
### ⏱️ Playtime Tracking
- Track usage for Today, This Week, This Month, and All Time
- View playtime leaderboard (most played games)
- Currently playing app indicator
- Per-app playtime statistics
- Reset all playtime data

### 💾 Backup & Restore
- Export all settings and categories to JSON file
- Import from backup file to restore configuration
- Cross-device backup compatibility

### 🖼️ Visual Customization
- 6 built-in backgrounds (Space, Nebula, Grid, Mountains, Abstract, Circuit)
- Custom image backgrounds from gallery
- Adjustable background opacity (0-100%)
- Transparent/see-through background option

<img width="3710" height="1937" alt="device info" src="https://github.com/user-attachments/assets/7142d0ea-e56e-4847-a70d-7b9703af5e86" />
### 📊 System Information
- Device model and manufacturer
- Android version and API level
- RAM usage (total, used, free)
- Internal storage usage
- Battery percentage and charging status
- WiFi signal strength and IP address
- App version and package info

### ⚡ Quick Settings Panel
- Volume control with slider (+/- buttons)
- Accessible via long-press on settings gear

### 🔧 Other Features
- Auto-start on Quest boot (toggle on/off)
- Edit Mode for batch category assignment
- App install/uninstall detection (auto-refresh)
- WiFi details on long-press

---

## 📸 Screenshots

<img width="3710" height="1895" alt="main" src="https://github.com/user-attachments/assets/a9acac6f-bdba-496e-9a4d-2d77f30fe1d9" />

<img width="3710" height="1937" alt="device info" src="https://github.com/user-attachments/assets/7142d0ea-e56e-4847-a70d-7b9703af5e86" />


---

## 🚀 Installation

### Prerequisites
- Meta Quest 1, 2, 3, or Pro
- Developer Mode enabled
- SideQuest or ADB access

### Steps

1. Download `EvolveLauncher.apk` from [Releases](https://github.com/JarJarBlinkz/EvolveLauncher/releases)

2. Install via SideQuest:
   - Open SideQuest on your computer
   - Drag and drop the APK
   - Click install

3. **Grant Usage Stats Permission (required for playtime tracking)**:
   ```bash
   adb shell pm grant com.jarjarblinkz.EvolveLauncher android.permission.PACKAGE_USAGE_STATS
