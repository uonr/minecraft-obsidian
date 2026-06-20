# Minecraft Obsidian

Link Minecraft signs to Obsidian notes and open them with a right-click.

The sign's own text is the link. Each vault keeps a `Minecraft Sign.md` file at its root mapping that text to a note. This is a client-side mod.

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

## How It Works

Vaults are discovered automatically from Obsidian's registry (`obsidian.json`). For each vault, the mod reads and maintains a mapping file at the vault
root:

```text
# Minecraft Sign
- `Base` → [[Minecraft/Base]]
- `Central Station` → [[Transit/Hub]]
- `Search Shops` → [link](obsidian://search?vault=Notes&query=tag:%23shop)
```

- The key (in backticks) is the sign's text. Backticks make the key boundary explicit, so any sign
  text is safe. A multi-line sign is matched as its lines joined with single spaces.
- The value depends on what you linked:
  - **A plain note** (`obsidian://open` with just a vault and file) is stored as a wikilink. Because
    it lives inside the vault, Obsidian rewrites it automatically when you move or rename the note —
    the sign keeps working.
  - **Any other Obsidian URL** (search, create, a heading/block anchor, advanced-uri, ...) is kept verbatim as a Markdown link.

You can edit `Minecraft Sign.md` by hand in Obsidian; the mod only needs to read it.

## Usage

First, in Obsidian copy the note's URL (Command Palette → "Copy Obsidian URL"), e.g.:

```text
obsidian://open?vault=Notes&file=Minecraft/Base
```

Then link a sign either way:

- **While placing**: hold Shift and place a sign, then write its text (for example `Base`). It is
  linked automatically once you finish editing.
- **An existing sign**: hold Shift and right-click a sign that already has text.

Either way the mod finds the vault from the copied URL and writes `` `Base` → [[Minecraft/Base]] ``
into that vault's `Minecraft Sign.md`. Shift + right-click again with a different URL to update it.

### Open a Linked Sign

Right-click a linked sign. The mod looks up the sign's text across your vaults and opens the note.

### Update a Link

Copy a different note's URL and Shift + right-click the sign again; the entry is rewritten.

### Remove a Link

Delete the entry from `Minecraft Sign.md` (in Obsidian, or any editor). Editing the sign's text
changes its key, which leaves the old entry orphaned — prune it the same way.

## Notes

- Only the front side of a sign is read.