# CatFrame
[![](https://jitpack.io/v/song682/CatFrame.svg)](https://jitpack.io/#song682/CatFrame)   

A modern rendering & UI framework for **Minecraft 1.7.10**, providing a full backport of the **1.8+ JSON model system**, an enhanced **multi‑layer item renderer**, and a modular **UI toolkit** for building complex GUIs.  
Designed as a foundational library for mods that require modern visuals and structured UI components.

---

## **✨ Features Overview**

### v0.3.0+
- **Type-safe BlockState Property System** — `Property<T>`, `BooleanProperty`, `IntegerProperty`, `EnumProperty`
- **CatBlockState** — state holder with O(1) neighbor jump-table (`setValue()` is a direct array lookup)
- **CatStateDefinition** — builder-pattern state manager with Cartesian-product state generation
- **StateBlockModel** — property-based model dispatch using `CatBlockState.toVariantKey()`
- **Backward-compatible** — all existing `IBlockStateProvider`, metadata, and model mappings still work

### v0.2.0+
- **1.8‑style JSON model system** (inheritance, elements, textures, display transforms)
- **Blockstate JSON** with variants, rotations, weighted randomness, multipart logic
- **Runtime state mapping** via `IBlockStateProvider`
- **Automatic model baking** into `BakedQuad`
- **Unified BlockStateModel / ItemModel interfaces** — clean dispatch, easy to extend
- **BlockStateModelPart** — direction-grouped quads
- **UniformRenderPipeline** — centralized AO → extension chain → Tessellator
- **Enhanced item rendering** with unlimited texture layers (`ItemModern`)
- **Modular UI framework** (Cyclable buttons, content panels, tab system)
- **Namespace‑based resource loading**
- **Mixin‑based integration** with vanilla block & item rendering

---

# **📦 Installation**

Add CatFrame as a dependency in your `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.song682:CatFrame:<version>'
}
```

Replace `<version>` with the latest release.

---

# **🧱 JSON Model System**

CatFrame backports the entire **1.8+ model pipeline** to 1.7.10.

### **Supported Features**
- `parent` inheritance chain  
- `elements` with per‑face UV, rotation, cullface  
- `textures` with recursive `#references`  
- `display` transforms for GUI, ground, first‑person, third‑person, fixed, shelf  
- `gui_light: "front" | "side"`  
- Automatic texture collection & stitching  

### **Example**
```json
{
  "parent": "block/cube_all",
  "textures": {
    "all": "minecraft:blocks/stone"
  }
}
```

---

# **🧩 Blockstate System**

CatFrame implements a full **blockstate JSON** system identical to 1.8+.

### **Features**
- Property‑based variants  
- `x` / `y` rotation  
- UV lock  
- Weighted random models  
- Multipart rendering  
- Metadata mapping for legacy blocks  

### **Example**
```json
{
  "variants": {
    "facing=north": { "model": "block/furnace", "y": 0 },
    "facing=east":  { "model": "block/furnace", "y": 90 }
  }
}
```

---

# **⚙️ Dynamic Runtime States (`IBlockStateProvider`)**

For modded blocks, CatFrame can dynamically compute blockstate properties at runtime.

```java
public class BlockModCake extends Block implements IBlockStateProvider {
    @Override
    public Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int meta) {
        return Map.of("bites", String.valueOf(meta));
    }
}
```

CatFrame will:
1. Read your properties  
2. Build a variant key (`bites=3`)  
3. Select the correct model  
4. Bake & render it  

No ISBRH. No TESR. No OpenGL.

---

# **🖼️ ItemModern — Enhanced Item Rendering**

`ItemModern` extends vanilla item rendering with:

- Unlimited texture layers  
- Per‑layer color tinting  
- 2D GUI + 3D in‑hand rendering  
- JSON model compatibility  

### **Example**
```java
setLayerTextureNames(
    "mymod:items/blade",
    "mymod:items/guard",
    "mymod:items/handle",
    "mymod:items/gem"
);
```

---

# **🖥️ UI Framework**

CatFrame includes a modular UI toolkit used by CreateWorldUI and available for any mod.

---

## **GuiCyclableButton\<T\>**

A type‑safe cycling button with scroll‑wheel support.

- Custom value‑to‑text formatting  
- Dynamic value lists  
- Update callbacks  
- On/off builder shortcut  

```java
GuiCyclableButton<Boolean> cheats = GuiCyclableButton.onOffBuilder()
    .initially(false)
    .build(201, x, y, 200, 20, (btn, v) -> setAllowCheats(v));
```

---

## **ContentPanelRenderer**

A shared renderer for structured panel layouts.

- Header & footer separators  
- Tiled backgrounds  
- Custom textures  
- One‑call `drawContentPanel()`  

```java
ContentPanelRenderer.drawContentPanel(x, top, width, bottom);
```

---

## **Tab System**

A complete multi‑tab GUI framework.

### Components
- `Tab` — core interface  
- `AbstractScreenTab` — base implementation  
- `TabManager` — switching, input routing, resize persistence  
- `TabBar` — customizable tab bar (solid color, tiled texture, custom tab buttons)  
- `TabRegistry` — register tabs from external mods  

### Example
```java
TabRegistry.registerTab(MyTab::new, 103, "mymod.tab.custom", 5);
```

---

# **📁 Resource Structure**

```
assets/<namespace>/
├── blockstates/
├── models/
│   ├── block/
│   ├── item/
│   └── builtin/
└── model_mappings.json
```

---

## **🔧 Architecture Overview**

- **VanillaModelManager** — core loader, baker, renderer; orchestrates BlockStateModel/ItemModel dispatch
- **Property / BooleanProperty / IntegerProperty / EnumProperty** (v0.3.0) — type-safe block state properties
- **CatBlockState** (v0.3.0) — state holder with O(1) neighbor jump-table, `toVariantKey()` for JSON matching
- **CatStateDefinition** (v0.3.0) — builder-pattern state definition manager
- **BlockStateModel** — block model interface: `collectParts(world, x, y, z, meta)` / `collectParts(world, x, y, z, state)`
  - `SingleBlockModel` — static model wrapper
  - `MetadataBlockModel` — metadata → variant dispatch
  - `StateProviderBlockModel` — dynamic IBlockStateProvider resolution (string maps)
  - **`StateBlockModel`** (v0.3.0) — property-based dispatch via `CatBlockState.toVariantKey()`
  - `MultipartBlockModel` — conditional multipart composition
- **BlockStateModelPart** — direction-grouped quad container
- **ItemModel** — item render interface
  - `ItemModelWrapper` — reuses BlockStateModel for item rendering
- **UniformRenderPipeline** — unified rendering: per-vertex AO → extension chain → Tessellator
- **ModelResolver** — parent chain resolution
- **BlockJsonModelBake** — element → BakedQuad
- **MixinRenderBlocks / MixinRenderItem** — rendering hooks
- **ItemModern** — enhanced item renderer
- **UI Components** — cycling buttons, panels, tabs

---

# **📜 License**
[MIT License](LICENSE).

---

# **📚 Related**
- Model System
- ItemModern
- UI Components
