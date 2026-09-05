CatFrame
<img align="right" alt="Logo" width="128" height="128" src="src/main/resources/assets/catframe/logo.png">
====

[![modrinth](https://raw.githubusercontent.com/song682/svg-bandage/refs/heads/main/Modrinth-Bandage-Small.svg)](https://modrinth.com/mod/catframe)[![curseforge](https://raw.githubusercontent.com/song682/svg-bandage/refs/heads/main/CurseForge-Bandage-Small.svg)](https://www.curseforge.com/minecraft/mc-mods/catframe)[![github](https://raw.githubusercontent.com/song682/svg-bandage/refs/heads/main/GitHub-Bandage-Small.svg)](https://github.com/song682/CatFrame)[![codeberg](https://raw.githubusercontent.com/song682/svg-bandage/refs/heads/main/CodeBerg-Bandage-Small.svg)](https://codeberg.org/song682/cat-frame)   
[![](https://jitpack.io/v/song682/CatFrame.svg)](https://jitpack.io/#song682/CatFrame)     

A modern rendering & UI framework for **Minecraft 1.7.10**. Backports the **1.8+ JSON model pipeline** and **1.21+ item state decision trees**, provides a **deferred render pipeline** with a per-quad extension API, a **custom texture atlas** system, a **type-safe BlockState** property system, a **data component** framework, a **tag** system with OreDict interop, and a full **component-based UI toolkit**.

---

## Modules

| Module | Key APIs | Description |
|---|---|---|
| **JSON Model System** | `IBlockStateProvider`, `CatModels`, `BakedModelCache` | Full 1.8+ model pipeline: `parent` inheritance, `elements` with per-face UV/rotation/cullface, `textures` with recursive `#references`, `display` transforms, blockstate variants & multipart. |
| **ItemState Decision Tree** | `IItemStateProvider`, `ItemStateNode` | 1.21.2+ style `items/` JSON — runtime decision tree (`condition`, `range_dispatch`, `select`, `composite`) that resolves a model per-frame from ItemStack properties. Extensible node & tint type registries. |
| **Uniform Render Pipeline** | `UniformRenderPipeline`, `RenderPhase`, `RenderSubmit` | Deferred command pipeline (Extract → Submit → Render). Block world rendering writes inline to vanilla chunk Tessellator; item/GUI paths use scoped command buffers with sorted batch flush. |
| **Render Extensions** | `IModelRenderExtension`, `RenderContext` | Per-quad extension chain — mods register extensions that modify color, brightness, culling, etc. before each quad is written. Thread-safe; exception-isolated. |
| **CatAtlas** | `CatAtlas`, `CatSprite`, `AtlasSource` | Custom texture atlas with pluggable sources: `SingleSource`, `DirectorySource`, `FilterSource`, `PalettedPermutationsSource`, `UnstitchSource`. Automatic texture collection & stitching. |
| **Type-Safe BlockState** | `Property<T>`, `CatStateDefinition`, `CatBlockState` | 1.8+ style typed properties (`BooleanProperty`, `IntegerProperty`, `EnumProperty`) with O(1) neighbor jump table. `CatStateInheritance` for base-class state propagation. |
| **Data Components** | `DataComponentType`, `DataComponents`, `DataComponentMap` | 1.21+ style per-ItemStack data components. Type-safe global registry, per-item defaults, NBT migration, network sync. Built-in: `ENCHANTMENT_GLINT`, `ITEM_MODEL`. |
| **Tag System** | `TagLoader`, `TagKey`, `OreDict2Tag` | Modern tag system (JSON-loaded, namespaced). Bidirectional OreDict ↔ Tag conversion for gradual migration. |
| **Recipe System** | `CatFrameRecipeManager`, `ShapedTagRecipe`, `ShapelessTagRecipe` | Tag-aware shaped/shapeless crafting & smelting recipes. Recipe removal API (by output, by predicate). |
| **UI Toolkit** | `Screen`, `Layout`, components, `OverlayManager` | Component-based GUI framework: `Screen` base class with focus navigation & event dispatch; layouts (`Grid`, `Linear`, `Frame`, `HeaderFooter`); widgets (buttons, edit boxes, scroll areas, selection lists, tabs, toasts); overlay system with auto-stacking for both Screen and HUD contexts. |
| **Language** | `LanguageRegister` | JSON lang file (`xx_xx.json`) loader — injects into Forge `LanguageRegistry` with resource-pack override support. |
| **Search Tree** | `TextSearchTree`, `SuffixArray` | Suffix-array / trie-based search tree for item/block lookup. |

## Dependency

```
Minecraft:     1.7.10
Forge:         10.13.4.1614
UniMixins:     0.2.1 (optional, for Mixin support)
Java:          8
```

## Installation

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.song682:CatFrame:<version>'
}
```

## License

**Source Code**: [MIT License](LICENSE).  
**Assets**: All rights reserved — see [LICENSE-Assets](LICENSE-Assets). Third-party character assets (Bluey) are excluded from the open-source license and may not be redistributed without permission from their respective rights holders.

## Credits

- [AmarokIce](https://github.com/AmarokIce) for the [JSON model system](https://github.com/AmarokIce/JsonModellegacy), licensed under MIT.