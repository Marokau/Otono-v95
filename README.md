# Otono v95

Otono is my personal MapleStory v95 server. I started it to explore this era of MapleStory at my own pace and to get as close as I can to how GMS v95 played at the time, early 2011.

This first release is the foundation. I wanted setup to be simple enough that someone could open the launcher and follow the steps without hunting through several guides. 

I'm working toward guided party quest bot crews, starting with Kerning City. I'll share those as I finish and test them in game.

Many PQ scripts are unfinished so the research part for authenticity to the original time period is the longest task here.

You can check out [Otono v83](https://github.com/Marokau/Otono-v83) for an example of the finished party quest experience.

## What Otono adds

- **Windows launcher** checks what you need, walks you through first-time setup, and enables **Start Game** once the server is ready.
- **All In One Shop** opens with `@aio` or `@aioshop` and lets you search for equipment and supplies.
- **In-game command guide** opens with `@help`. Select a command to see how to type it and what it does.
- **Adjustable EXP** is available through **Config** in the launcher. Restart the server after editing the settings, then use `@rates` in game to check the monster EXP multiplier.

## First-time setup on Windows

1. Open **Otono.cmd**. The launcher shows what you still need before you can play.
2. Install a **Java 21 JDK** and reopen the launcher if it was already open.
3. Install the compatible **GMS v95 game files** into the project's **game** folder. The launcher also recognizes **game/MapleStory** if the installer creates it. (Go to archive.org > search for GMS Setup > Scroll down and download v95.)
4. Click **Initial Setup**. It prepares the server and installs the included Kinoko launcher files. The first setup needs internet access.
5. Click **Start Server**, wait until **Start Game** is available, then click it. SQLite sets itself up when the server starts.
6. At login, pick a username and password. The first attempt registers the account and may show an account-not-registered message. Dismiss it and log in again with the same credentials.

On later visits, open **Otono.cmd** and use **Start Server** and **Start Game**.

## Layout

| Folder | Purpose |
| --- | --- |
| `server/` | Java server source, Maven wrapper, data definitions, generated build output and local SQLite database |
| `client/` | Kinoko launcher/DLL source, pinned native dependencies, and the official prebuilt release in bin/ |
| `game/` | Install location for your game files. The folder includes setup instructions, and installed files stay local. |
| `docs/` | Setup provenance and Otono documentation |
| `launcher/` | Small Windows launcher source and locally generated executable (bin is ignored) |

## Foundation

- [iw2d/Kinoko server](https://github.com/iw2d/kinoko)
- [iw2d/Kinoko client](https://github.com/iw2d/kinoko_client)

Upstream notices and native dependency licenses remain with their source. This repository starts its own Git history at the clean setup checkpoint.
