# Ghost Client packs

- `GhostClient_UI.mcpack`: restyles the Minecraft main menu (ghost logo, dark night
  panorama with HDR copies, Ghost-blue buttons). Texture-only, so it keeps working
  across Minecraft updates. Install: open the file with Ghost Client or Minecraft,
  then enable it under Settings > Global Resources.
- `tools/build_ghost_ui.py`: regenerates the pack textures from the Ghost logo.

Keep the UUIDs in `ghost-ui-src/manifest.json` when rebuilding so updates replace
the installed pack instead of adding a second copy.
