# Community Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20TypeWriter-blue)

**Community Extension** is a unified community tool suite for **TypeWriter**, engineered for **BTC Studio** infrastructure. It centralizes player engagement tools including Discord synchronization and bug reporting.

---

## 🚀 Key Features

### 🎮 Discord Integration
- **Account Linking**: Sync ranks and verify accounts between Minecraft and Discord.
- **Role Sync**: Automatically update Discord roles based on in-game status.

### 🐛 Bug Reporting
- **In-Game Reporting**: Customizable reporting menus with Dialog integration.
- **Webhook Integration**: Send bug reports directly to Discord channels.

### 💬 Chat & Console
- **Chat Sync**: Synchronize in-game chat with Discord channels.
- **Console Channel**: Stream console logs to private Discord channels for monitoring.

---

## ⚙️ Configuration

Community Extension configuration is managed via TypeWriter's manifest system.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/CommunityExtension.git
cd CommunityExtension

# Build the project
./gradlew clean build
```

### Artifact Locations:
- `build/libs/CommunityExtension-[Version].jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://borntocraft.fr)** - Maintenance and specialized optimizations.

---

## 📜 License
Licensed under the **MIT License**.
