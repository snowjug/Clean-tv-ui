# 📺 Clean TV UI

> **A bespoke, Apple TV-inspired launcher and performance optimization suite for Android TV & Mi TV.**  
> Ad-free, bloat-free, silky smooth, and privacy-first.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV%2011%2B-blue?style=flat-square&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Style-Apple%20TV%20Glassmorphism-black?style=flat-square&logo=apple" alt="Apple TV Style">
  <img src="https://img.shields.io/badge/Launcher%20Size-515%20KB-success?style=flat-square" alt="Size">
  <img src="https://img.shields.io/badge/Internet%20Permission-None%20(100%25%20Offline)-brightgreen?style=flat-square" alt="Offline">
  <img src="https://img.shields.io/badge/License-MIT-orange?style=flat-square" alt="License">
</p>

---

## 🌟 Overview

**Clean TV UI** transforms bloated, ad-ridden smart TV interfaces (like Xiaomi PatchWall or stock Google TV) into a distraction-free, ultra-fast streaming hub tailored specifically for a premium living room experience.

Built with lightweight native Java and zero telemetry, it restores instant TV boot speeds, frees up memory, and elevates the screen with frosted glassmorphism and rotating vector landscape scenery.

---

## ✨ Key Features & Customizations

### 🍏 1. Apple TV Frosted Glassmorphism Dock
- Sleek, translucent blurred glass capsule wrapping your top-row apps (`dock_glass: true`, `dock_even_padding: true`).
- Clean separation between active dock favorites and your broader application grid.

### 🌄 2. Scenes Dynamic Rotating Wallpapers
- Bundled with **14 dark minimalist vector landscape wallpapers**.
- Automatically rotates hourly in the background without waking or polling services (`wallpaper_rotation_minutes: 60`).

### 🌦️ 3. Live Weather & Air Quality Card
- Modular weather card integration (`addons/weather`) powered by [Open-Meteo](https://open-meteo.com/).
- Displays current temperature, feels-like, live conditions, US AQI pill, and 3-day forecast.
- **Zero background lag**: queries weather only when on screen; zero internet permissions in the core launcher.

### ⚡ 4. Safe Mi TV Boot Acceleration & Debloat
- Tailored debloat profile tested on **Xiaomi Mi TV 4K (`MiTV-AXSO2` / `dangalUHD`)**.
- **23 background ad/telemetry daemons safely disabled** using `pm disable-user`.
- **Zero breakage**: preserves Miracast (`com.xiaomi.mitv.smartshare.wifidisplay`), Chromecast built-in, TV inputs / HDMI tuner, Bluetooth remote pairing, and all major streaming services (YouTube, JioHotstar, Sony LIV, Netflix, Prime Video, Spotify, etc.).

### 🎛️ 5. Dedicated Remote Quick Settings Panel
- Maps your physical TV remote's **Settings (Gear) button** directly to CleanYourTV's native quick panel (`settings_key_panel: true`).
- Instant access to Inputs, Network, Display, Apps, and Launcher tweaks without wading through heavy manufacturer menus.

### 🔀 6. Drag-and-Drop Tile Reordering
- Long-press **OK** or press the **Menu** button on any tile to move apps anywhere (`tile_menu: true`).
- Effortlessly organize your frequently used apps directly into the Apple glass dock.

### 🔒 7. Privacy & Zero-Bloat Architecture
- Core launcher binary is under **550 KB** with **NO `INTERNET` permission**.
- No telemetry, no ad tracking, no video auto-play, and no background memory leaks. Designed to fly even on 1 GB RAM TVs.

---

## 📺 Tested Hardware Specifications

| Component | Specification |
|---|---|
| **Device Model** | Xiaomi Mi TV 4K (`MiTV-AXSO2`) |
| **Board / Device Codename** | `dangalUHD` |
| **Android Version** | Android TV 11 (API Level 30) |
| **Firmware Build** | `RTT0.211201.001.1150 release-keys` |
| **Connection** | Wireless ADB over Wi-Fi (`5555`) |

---

## 🚀 Quick Start (Wireless 1-Line Deployment)

### Prerequisites
1. Ensure your TV and computer are on the same Wi-Fi network.
2. On your TV, navigate to:  
   **Settings** ➔ **Device Preferences** ➔ **About** ➔ click **Build** 7 times to enable Developer Options.
3. In **Developer Options**, switch **USB debugging** to **ON** and accept your computer's authorization prompt.

---

### Windows (PowerShell)

Open PowerShell in the project directory and run:

```powershell
# Set your TV IP and deploy the launcher + Scenes theme
$env:TV_HOST="192.168.1.34"
.\launcher\build-and-install.ps1; .\tools\theme.ps1 themes\scenes
```

#### Apply the Mi TV Safe Debloat (Boosts Boot Speed):
```powershell
.\tools\disable.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-axso2\debloat.txt
```

#### Install the Live Weather Card:
```powershell
cd addons\weather; .\gradlew assembleRelease; cd ..\..
$env:TV_HOST="192.168.1.34"
.\tools\venv\Scripts\python.exe .\tools\tv.py --push addons\weather\app\build\outputs\apk\release\app-release.apk /data/local/tmp/addon.apk
.\tools\venv\Scripts\python.exe .\tools\tv.py "pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk"
```

---

### macOS & Linux (Bash)

```bash
export TV_HOST="192.168.1.34"
./launcher/build-and-install.sh && ./tools/theme.sh themes/scenes
./tools/disable.sh examples/xiaomi-mitv-axso2/debloat.txt
```

---

## 🛠️ Configuration (`themes/scenes/config.json`)

All visual styling and interaction settings are fully declarative:

```json
{
  "theme_name": "Scenes",
  "wallpaper": true,
  "wallpaper_rotation_minutes": 60,
  "dock_glass": true,
  "dock_even_padding": true,
  "tile_menu": true,
  "settings_key_panel": true,
  "settings_key_keycodes": [176, 83],
  "card_color": "#1c222d",
  "card_focus_color": "#ffffff",
  "card_title_focused_color": "#11141a"
}
```

| Key | Description |
|---|---|
| `dock_glass` | Enables the Apple-style frosted glassmorphism dock wrapper |
| `wallpaper_rotation_minutes` | Interval in minutes to rotate wallpapers (`60` for hourly) |
| `tile_menu` | Enables long-press OK / Menu to reorder tiles |
| `settings_key_panel` | Intercepts remote Settings key to open CleanYourTV quick panel |
| `settings_key_keycodes` | Keycodes for physical remote Settings / Menu buttons (`[176, 83]`) |

---

## 🔄 Safe Restoration / Undo

Everything disabled via this setup uses `pm disable-user --user 0`. **Nothing is ever permanently deleted or uninstalled.**

### Restore Stock Bloatware Packages:
```powershell
.\tools\restore.ps1 -TvHost 192.168.1.34 examples\xiaomi-mitv-axso2\debloat.txt
```

### Restore Stock Launcher:
```powershell
$env:TV_HOST="192.168.1.34"
.\tools\venv\Scripts\python.exe .\tools\tv.py "pm enable --user 0 com.google.android.tvlauncher; cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"
```

---

## 🔒 Security Best Practice

Once your TV is set up and looking great, turn off debugging:
1. Go to **Settings** ➔ **Device Preferences** ➔ **Developer Options**.
2. Toggle **USB debugging** to **OFF**.
3. Clean TV UI and all custom shortcuts will continue to work normally without keeping ADB ports open on your network.

---

## 👏 Credits & Acknowledgements

This personalized setup is built upon and inspired by the remarkable work of the open-source community:

- **Upstream Project**: [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by [@Gathin23](https://github.com/Gathin23) — for the foundational, ultra-efficient Android TV launcher and debloat methodology.
- **Weather Add-on**: [@Sumyabazar1](https://github.com/Sumyabazar1) — for the Open-Meteo Android TV module card.
- **Typography**: [Outfit](https://fonts.google.com/specimen/Outfit) typeface by SIL Open Font License.
- **Curated & Customized by**: [@snowjug](https://github.com/snowjug)

---

## 📄 License

This repository is distributed under the [MIT License](LICENSE).
