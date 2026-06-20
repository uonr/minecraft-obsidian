package com.uonr.minecraftobsidian;

import java.nio.file.Path;

/** A single Obsidian vault discovered from the local Obsidian registry. */
record ObsidianVault(String id, String name, Path root) {
}
