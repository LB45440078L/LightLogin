# 🔐 LightLogin

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.17.1--26.2-62B47A?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft">
  <img src="https://img.shields.io/badge/Java-25%2B-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spigot-API-red?style=for-the-badge&logo=spigotmc&logoColor=white" alt="Spigot">
  <img src="https://img.shields.io/github/license/LB45440078L/LightLogin?style=for-the-badge" alt="License">
</p>

<p align="center">
  <strong>Optimised and Safe Authentication Software for SpigotMC</strong>
</p>

<p align="center">
  A modular, extensible authentication system for Minecraft servers,
  designed with security, performance and integration in mind.
</p>

<p align="center">
  <a href="https://github.com/LB45440078L/LightLogin">Repository</a>
  •
  <a href="https://github.com/LB45440078L/LightLogin/issues">Issues</a>
  •
  <a href="https://github.com/LB45440078L/LightLogin/releases">Releases</a>
</p>

---

## ✨ Overview

**LightLogin** is a Java-based authentication plugin for Spigot Minecraft servers.

It provides a complete authentication layer between the Minecraft client and server, handling player registration, login sessions, password management, verification workflows and authentication state.

The project is designed around a modular architecture, making individual subsystems independently maintainable and allowing other plugins to integrate with LightLogin through its public API.

### Why LightLogin?

* 🔐 Secure password handling
* ⚡ Efficient database access
* 🧩 Modular architecture
* 🌍 Multi-language support
* 📧 Optional e-mail functionality
* 🗄️ Multiple database backends
* 🔌 Public developer API
* 🛠️ Extensive configuration
* 📦 Shaded dependency isolation

---

## 📊 Project Stats

<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=LB45440078L&repo=LightLogin&theme=transparent&hide_border=true" alt="Repository Stats">
</p>

<p align="center">
  <img src="https://img.shields.io/github/commit-activity/m/LB45440078L/LightLogin?style=flat-square">
  <img src="https://img.shields.io/github/last-commit/LB45440078L/LightLogin?style=flat-square">
  <img src="https://img.shields.io/github/repo-size/LB45440078L/LightLogin?style=flat-square">
  <img src="https://img.shields.io/github/issues/LB45440078L/LightLogin?style=flat-square">
  <img src="https://img.shields.io/github/forks/LB45440078L/LightLogin?style=flat-square">
  <img src="https://img.shields.io/github/stars/LB45440078L/LightLogin?style=flat-square">
</p>

---

## 🚀 Features

### 🔑 Authentication

LightLogin provides the fundamental authentication lifecycle required by offline-mode Minecraft servers.

* Player registration
* Password authentication
* Login sessions
* Logout / session removal
* Password changes
* Password reset workflows
* Temporary passwords
* Account verification
* CAPTCHA verification support
* Authentication-state enforcement

### 🛡️ Security

Security is a core part of the project architecture.

LightLogin separates authentication logic from persistence and exposes dedicated encryption utilities. Password-related operations are designed around modern cryptographic libraries rather than storing plaintext credentials.

The project also includes mechanisms for controlling sensitive authentication information in server logging and player-facing interactions.

> **Important:** No authentication plugin can compensate for an insecure server configuration. Always use strong passwords, restrict administrative access and keep your server software and dependencies updated.

### 🗄️ Database Support

LightLogin contains an abstraction layer for persistent account storage.

Supported database environments include:

| Database       | Use case                          |
| -------------- | --------------------------------- |
| **SQLite**     | Small / standalone servers        |
| **MySQL**      | Production / network environments |
| **PostgreSQL** | Production / advanced deployments |

Database connectivity uses **HikariCP** for connection pooling.

### 📧 E-mail Integration

Optional SMTP functionality allows LightLogin to provide account-related e-mail workflows, including password-reset functionality.

### 🌍 Internationalisation

Language configuration is externalised through YAML files.

Included language configurations currently include:

* 🇬🇧 English
* 🇮🇹 Italian
* 🇫🇷 French
* 🇪🇸 Spanish
* 🇵🇹 Portuguese
* 🇷🇺 Russian
* 🇮🇱 Hebrew
* 🇵🇭 Filipino

Additional translations can be created through the configuration system.

---

# 📥 Installation

## Requirements

* **SpigotMC**
* Minecraft **1.17.1 – 26.2**
* Java **25+**
* Latest releases may require **Java 25**

LightLogin is currently marked as **not Folia-supported**.

## Installation Steps

1. Download the latest `.jar` from the [Releases](https://github.com/LB45440078L/LightLogin/releases) page.
2. Stop your Minecraft server.
3. Place the JAR into:

```text
/plugins/
```

4. Start the server.
5. Configure LightLogin inside:

```text
/plugins/LightLogin/
```

6. Restart the server after changing configuration where required.

---

# 🎮 Commands

LightLogin registers the following commands:

| Command           | Aliases                        | Description                   |
| ----------------- | ------------------------------ | ----------------------------- |
| `/login`          | `/l`, `/authenticate`, `/auth` | Authenticate                  |
| `/register`       | `/reg`                         | Register an account           |
| `/unregister`     | `/unreg`                       | Remove an account             |
| `/lightlogin`     | `/ll`                          | Plugin information            |
| `/changepassword` | `/changepsw`                   | Change password               |
| `/unlogin`        | `/unlog`, `/unl`, `/ul`        | Remove login session          |
| `/email`          | —                              | Configure personal e-mail     |
| `/resetpassword`  | `/resetpsw`                    | Reset password through e-mail |
| `/temppassword`   | `/temppsw`                     | Generate a temporary password |
| `/login-data`     | `/logindata`, `/userdata`      | Read account data             |
| `/verify`         | —                              | CAPTCHA verification          |

The exact permissions and behaviour can be configured according to the server setup.

---

# ⚙️ Configuration

After the first startup, LightLogin generates its configuration files inside the plugin's data directory.

Typical structure:

```text
plugins/
└── LightLogin/
    ├── config.yml
    ├── ...
    └── database/
```

The repository includes language templates such as:

```text
config_english.yml
config_italian.yml
config_french.yml
config_spanish.yml
config_portuguese.yml
config_russian.yml
config_hebrew.yml
config_filipino.yml
```

Configuration should be edited carefully and the server should be restarted when a setting requires plugin reinitialisation.

---

# 🧩 Developer API

LightLogin exposes a public API under:

```java
top.cmarco.lightlogin.api
```

The API currently contains authentication-related events and utility classes.

Available API components include:

```text
AuthenticationCause
LoginUtils
PlayerAuthenticateEvent
PlayerRegisterEvent
PlayerUnauthenticateEvent
PlayerWrongPasswordEvent
UnregisterEvent
```

## Listening for Authentication

For example, another Bukkit/Spigot plugin can listen for successful authentication:

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import top.cmarco.lightlogin.api.PlayerAuthenticateEvent;

public class AuthenticationListener implements Listener {

    @EventHandler
    public void onAuthenticate(PlayerAuthenticateEvent event) {

        Player player = event.getPlayer();

        player.sendMessage("Welcome back, " + player.getName() + "!");

    }
}
```

The event also exposes the reason/cause of authentication:

```java
@EventHandler
public void onAuthenticate(PlayerAuthenticateEvent event) {

    AuthenticationCause cause = event.getAuthenticationCause();

    // Handle authentication according to the cause
}
```

## Registration Events

Plugins can also react to account registration:

```java
@EventHandler
public void onRegister(PlayerRegisterEvent event) {

    Player player = event.getPlayer();

    // Your integration logic
}
```

Other available events allow integrations to react to:

* Authentication
* Unauthentication
* Registration
* Wrong passwords
* Account removal

This allows external plugins to build functionality around LightLogin without modifying its internal implementation.

---

# 📦 Maven Integration

LightLogin is built using Maven.

The project uses:

```xml
<groupId>top.cmarco</groupId>
<artifactId>LightLogin</artifactId>
```

The current project version is:

```text
1.3.2.3
```

For development against the API, use the repository as a dependency source or compile against the LightLogin JAR.

Example:

```xml
<dependency>
    <groupId>top.cmarco</groupId>
    <artifactId>LightLogin</artifactId>
    <version>1.3.2.3</version>
    <scope>provided</scope>
</dependency>
```

> The API package is intended for plugin integrations. Avoid depending directly on internal implementation packages unless you specifically require implementation-level access.

---

# 🏗️ Architecture

LightLogin follows a modular package structure:

```text
top.cmarco.lightlogin
│
├── api
│   ├── AuthenticationCause
│   ├── LoginUtils
│   ├── PlayerAuthenticateEvent
│   ├── PlayerRegisterEvent
│   ├── PlayerUnauthenticateEvent
│   ├── PlayerWrongPasswordEvent
│   └── UnregisterEvent
│
├── command
├── configuration
├── data
├── database
├── encrypt
├── library
├── listeners
├── log
├── mail
├── network
└── world
```

This separation keeps responsibilities isolated:

```text
                    ┌────────────────────┐
                    │   Minecraft Player │
                    └─────────┬──────────┘
                              │
                              ▼
                    ┌────────────────────┐
                    │   Bukkit Events    │
                    │    & Commands      │
                    └─────────┬──────────┘
                              │
                              ▼
                    ┌────────────────────┐
                    │ Authentication     │
                    │     Logic          │
                    └──────┬─────┬───────┘
                           │     │
              ┌────────────┘     └────────────┐
              ▼                               ▼
     ┌─────────────────┐             ┌─────────────────┐
     │ Security /      │             │ Configuration / │
     │ Encryption      │             │ Language        │
     └────────┬────────┘             └─────────────────┘
              │
              ▼
     ┌─────────────────┐
     │ Database Layer  │
     └────────┬────────┘
              │
       ┌──────┼──────┐
       ▼      ▼      ▼
    SQLite  MySQL PostgreSQL
```

---

# ⚡ Dependency Isolation

The final JAR uses Maven Shade with package relocation.

Libraries such as:

```text
HikariCP
Bouncy Castle
PostgreSQL
Jakarta Mail
Angus Mail
Libby
```

are relocated into the LightLogin namespace where appropriate.

This reduces the likelihood of dependency conflicts between LightLogin and other plugins running inside the same JVM.

---

# 🛠️ Building From Source

Clone the repository:

```bash
git clone https://github.com/LB45440078L/LightLogin.git
cd LightLogin
```

Build with Maven:

```bash
mvn clean package
```

The resulting plugin JAR will be generated inside:

```text
target/
```

---

# 🧪 Development

The project is structured as a standard Maven Java project:

```text
LightLogin/
├── pom.xml
├── LICENSE
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── top/cmarco/lightlogin/
        └── resources/
            ├── plugin.yml
            └── configuration files
```

The main plugin entry point is:

```java
top.cmarco.lightlogin.LightLoginPlugin
```

---

# 🔒 Security Philosophy

LightLogin is designed around several principles:

**Never trust client state**

Authentication state is maintained server-side.

**Never store plaintext passwords**

Credentials should be represented through password hashes rather than plaintext values.

**Separate concerns**

Authentication, persistence, configuration and external services are implemented as separate subsystems.

**Minimise dependency conflicts**

Runtime dependencies are isolated through Maven shading and relocation.

**Expose controlled integration points**

The public API provides events and utilities for external plugins without requiring direct modification of LightLogin internals.

---

# 📜 License

LightLogin is distributed under the **GNU General Public License v3.0**.

```text
Copyright © 2024 CMarco

LightLogin - Optimised and Safe SpigotMC Software for Authentication
```

See [`LICENSE`](LICENSE) for the complete license text.

---

# 🤝 Contributing

Contributions, bug reports and feature suggestions are welcome.

Before opening an issue:

1. Check existing issues.
2. Verify that the problem is reproducible.
3. Include relevant server and Java versions.
4. Include relevant console errors or stack traces.
5. Avoid posting passwords, credentials or other sensitive information.

Pull requests should keep changes focused and maintain the existing project architecture.

---

# 🐛 Bug Reports

Found a problem?

Open an issue:

**https://github.com/LB45440078L/LightLogin/issues**

Please include:

```text
Minecraft version:
Spigot version:
Java version:
LightLogin version:

Description:
Steps to reproduce:
Expected behaviour:
Actual behaviour:

Relevant logs:
```

---

# ⭐ Support the Project

If LightLogin is useful to you, consider giving the repository a ⭐ on GitHub.

It helps the project gain visibility and provides useful feedback about interest in the software.

<p align="center">

**Made with ☕ and Java by CMarco**

<a href="https://github.com/LB45440078L/LightLogin">
  <img src="https://img.shields.io/badge/View%20on-GitHub-181717?style=for-the-badge&logo=github" alt="GitHub">
</a>

</p>
