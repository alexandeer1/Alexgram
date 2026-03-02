<p align="center">
  <img src="[https://i.ibb.co/MDkydQLH/Chat-GPT-Image-Feb-23-2026-02-00-54-AM.png" alt="Alexgram Logo" width="120" height="120">
</p>
<h1 align="center">Alexgram</h1>

<p align="center">
  <b>✨ An Unofficial Telegram Client for Android — Reimagined ✨</b>
</p>

<p align="center">
  <i>Feature-rich • Privacy-focused • Open Source</i>
</p>

<p align="center">
  <a href="https://t.me/AlexgramApp"><img src="https://img.shields.io/github/v/release/alexandeer1/Alexgram?style=for-the-badge&logo=github&color=0088cc&labelColor=1a1a2e" alt="Latest Release"></a>
  <a href="https://github.com/alexandeer1/Alexgram/actions"><img src="https://img.shields.io/github/actions/workflow/status/alexandeer1/Alexgram/staging.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=Build&color=28a745&labelColor=1a1a2e" alt="Build Status"></a>
  <a href="https://github.com/alexandeer1/Alexgram/blob/master/LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue?style=for-the-badge&logo=gnu&logoColor=white&color=5865F2&labelColor=1a1a2e" alt="License"></a>
  <a href="https://github.com/alexandeer1/Alexgram/stargazers"><img src="https://img.shields.io/github/stars/alexandeer1/Alexgram?style=for-the-badge&logo=apachespark&logoColor=gold&color=FFD700&labelColor=1a1a2e" alt="Stars"></a>
  <a href="https://github.com/alexandeer1/Alexgram/fork"><img src="https://img.shields.io/github/forks/alexandeer1/Alexgram?style=for-the-badge&logo=git&logoColor=white&color=FF6B6B&labelColor=1a1a2e" alt="Forks"></a>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-download">Download</a> •
  <a href="#%EF%B8%8F-build-from-source">Build</a> •
  <a href="#-contributing">Contributing</a> •
</p>

---

## 📖 About

**Alexgram** is an unofficial, open-source Telegram client for Android built on top of [Telegram](https://github.com/DrKLO/Telegram). It extends the official Telegram experience with powerful additional features, enhanced customization options, and a focus on user privacy — all while maintaining full compatibility with the Telegram ecosystem.

> [!NOTE]
> Alexgram is **not** affiliated with, endorsed by, or connected to Telegram FZ-LLC in any way. It is an independent, community-driven project.

---

## ⚡ Features

<table>
  <tr>
    <td width="50%">

### 🎨 Customization
- Advanced theming engine
- Custom font support
- UI element customization
- Configurable chat layouts

</td>
    <td width="50%">

### 🔒 Privacy & Security
- Enhanced privacy controls
- Message read receipts management
- Anti-recall for deleted messages
- Ghost mode capabilities

</td>
  </tr>
  <tr>
    <td width="50%">

### 🚀 Performance
- Optimized resource usage
- Faster media loading
- Improved caching system
- Smooth animations & transitions

</td>
    <td width="50%">

### 🛠️ Advanced Tools
- Built-in message translator
- Extended media options
- Custom sticker management
- Developer-friendly debug tools

</td>
  </tr>
</table>

---

## 📥 Download

Get the latest version of Alexgram through any of the following channels:

| Channel | Description | Link |
|:--------|:------------|:-----|
| 🏷️ **Alexgram Channel** | Stable releases | [Download Latest](Https://t.me/AlexgramApp) |
| ⚙️ **Alexgram Chat** | Chats | [View Artifacts](Https://t.me/Alexgram_chat) |

---

## 🏗️ Build from Source

### Prerequisites

| Requirement | Version |
|:------------|:--------|
| Android Studio | Latest stable |
| JDK | 17+ |
| Android SDK | API 34+ |
| Telegram API Keys | [Get from my.telegram.org](https://my.telegram.org/auth) |

### Step 1 — Configure API Credentials

Obtain your `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` from the [Telegram Developer Portal](https://my.telegram.org/auth), then create a `local.properties` file in the project root:

```properties
TELEGRAM_APP_ID=<your_telegram_app_id>
TELEGRAM_APP_HASH=<your_telegram_app_hash>
```

### Step 2 — Configure APK Signing

Replace `release.keystore` with your own keystore and add the signing configuration to `local.properties`:

```properties
KEYSTORE_PASS=<your_keystore_password>
ALIAS_NAME=<your_alias_name>
ALIAS_PASS=<your_alias_password>
```

### Step 3 — Firebase Cloud Messaging *(Optional)*

For push notification support via FCM, replace `TMessagesProj/google-services.json` with your own Firebase configuration file.

### Step 4 — Build

Open the project in **Android Studio** and hit **▶️ Run** — or build from the command line:

```bash
./gradlew assembleRelease
```

<details>
<summary><b>🔄 CI/CD — Building with GitHub Actions</b></summary>
<br>

1. Replace `TMessagesProj/release.keystore` with your keystore file.

2. Create your `local.properties` with all required keys and Base64 encode the contents.

3. Configure these **GitHub Action Secrets**:

   | Secret | Description |
   |:-------|:------------|
   | `LOCAL_PROPERTIES` | Base64-encoded `local.properties` content |
   | `HELPER_BOT_TOKEN` | Telegram bot token from [@BotFather](https://t.me/Botfather) |
   | `HELPER_BOT_TARGET` | Primary Telegram chat ID for notifications |
   | `HELPER_BOT_CANARY_TARGET` | Chat ID for test builds and metadata |

4. Trigger the **Release Build** workflow from the Actions tab.

</details>

---

## 🤝 Contributing

Contributions are welcome! Whether it's bug reports, feature requests, or pull requests — every contribution helps make Alexgram better.

1. **Fork** the repository
2. **Create** your feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

---

## ⚠️ Disclaimer

> [!CAUTION]
> **Alexgram** is an **unofficial** third-party Telegram client. It is **not** affiliated with, endorsed by, sponsored by, or in any way officially connected to **Telegram FZ-LLC** or any of its subsidiaries or affiliates.
>
> - The official Telegram website can be found at [telegram.org](https://telegram.org).
> - The name "Telegram" as well as related names, marks, emblems, and images are registered trademarks of their respective owners.
> - Using unofficial clients may carry inherent risks, including but not limited to account restrictions imposed by Telegram. **Use at your own risk.**
> - The developers of Alexgram provide this software **"as is"** without any warranty of any kind, express or implied. The developers are **not responsible** for any damages, data loss, account restrictions, or any other consequences resulting from the use of this application.
> - This project is distributed under the [GNU General Public License v3.0](LICENSE). By using or contributing to this project, you agree to be bound by the terms of this license.

---

<p align="center">
  <sub>Made with ❤️ by the Alexgram community</sub>
</p>

<p align="center">
  <a href="https://github.com/alexandeer1/Alexgram/stargazers">
    <img src="https://img.shields.io/badge/⭐_Star_this_repo-If_you_find_it_useful!-yellow?style=for-the-badge&labelColor=1a1a2e" alt="Star this repo">
  </a>
</p>
