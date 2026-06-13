# Minecraft Obsidian

Link Minecraft signs to Obsidian URLs and open them with a right-click.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.229 or newer
- Obsidian installed on the client machine

## Installation

Build the mod:

```sh
./gradlew build
```

Then copy the jar from:

```text
build/libs/minecraft_obsidian-1.0.0.jar
```

to the `mods` directory of your Minecraft instance.

For multiplayer server-backed storage, install the same jar on both the client and the server. If the server does not have the mod installed, the client automatically falls back to local storage.

## Usage

### Link a New Sign

1. Copy an Obsidian URL, for example:

   ```text
   obsidian://open?vault=Notes&file=Minecraft/Base
   ```

2. Hold a sign item.
3. Hold Shift and place the sign.
4. The sign is linked after placement succeeds.

### Open a Linked Sign

Right-click a linked sign.

The mod opens the associated `obsidian://` URL. Vanilla sign interaction may still happen, so editable signs can still open their edit screen.

### Update a Linked Sign

1. Copy a new `obsidian://...` URL.
2. Hold Shift.
3. Right-click an already linked sign.

The sign's link is updated to the URL currently in the clipboard.

### Remove a Link

Break the linked sign.

## Storage Modes

Minecraft Obsidian supports two storage modes:

- **Server-backed storage**: used in singleplayer and on servers that have this mod installed. Links are stored on the sign block entity as a NeoForge data attachment and can be shared by players who have the client mod.
- **Local fallback storage**: used on multiplayer servers that do not have this mod installed. Links are stored in the client's config directory at `config/minecraft_obsidian/links.json`.
