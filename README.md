# ChatCopy

Retyping usernames from chat into logs gets old. This Forge 1.8.9 mod copies chat with a right-click.

| Action (chat open) | Copies |
| :--- | :--- |
| Right-click a word | That word. Edge punctuation is stripped, so `Steve:` gives `Steve` |
| Shift + right-click | The whole message, colour codes removed |

A grey `Copied: ...` line confirms it. Client-side only; nothing is sent to the server. Works on vanilla Forge and on Feather/Dawn, which move the chat around.

## Install

Drop `chatcopy-1.0.0.jar` from [Releases](../../releases) into your 1.8.9 Forge `mods` folder.

## Build

```
./gradlew build
```

The jar lands in `build/libs/`. Gradle fetches Java 8 itself.
