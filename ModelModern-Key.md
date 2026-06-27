Let我先了解两边的模型加载系统。


现在让我看看高版本的模型加载核心代码。


好的，我已经深入对比了两边的模型加载系统。以下是主要区别：

---

## 高版本 (26.1.2) vs CatFrame 模型加载系统对比

### 1. 整体架构：流水线 vs 单体

**高版本** 是一个高度分层的 **异步流水线** 架构：
- [ModelManager](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/ModelManager.java) 是总调度器，实现 `PreparableReloadListener`，整个 reload 过程全部用 `CompletableFuture` 串联，支持多线程并行加载
- 加载 → 依赖发现 → 烘焙 → 应用，每个阶段都是独立的异步 step

**CatFrame** 的 [VanillaModelManager](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/VanillaModelManager.java) 是**同步单线程**模型：
- 依赖 Forge 事件钩子（`TextureStitchEvent.Pre/Post`）在特定时机手动触发各阶段
- 没有并行加载能力，所有烘焙在主线程完成

### 2. 模型解析：接口抽象 vs 直接操作

**高版本** 有清晰的接口层次：
- [UnbakedModel](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/UnbakedModel.java) — 未烘焙模型接口（AO、guiLight、transforms、geometry、parent）
- [ResolvedModel](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/ResolvedModel.java) — 解析后的模型（处理了 parent 链继承，缓存了 AO/guiLight/transforms/geometry 等）
- [CuboidModel](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/cuboid/CuboidModel.java) — 具体实现，对应 JSON 中的 elements 模型
- `UnbakedGeometry` / `QuadCollection` — 几何数据抽象，支持自定义几何体

**CatFrame** 则比较扁平：
- [ModelJson](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/ModelJson.java) 既是数据容器又承载解析，没有接口抽象
- [ModelResolver](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/ModelResolver.java) 负责 JSON 加载和 parent 链解析，但解析结果直接是 `ModelJson` 对象，没有"未烘焙/已解析"的分层

### 3. 依赖发现与 parent 链处理

**高版本** 有专门的 [ModelDiscovery](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/ModelDiscovery.java)：
- 从 root 模型出发，BFS 遍历整个 parent 依赖图
- 有**循环依赖检测**（`propagateValidity`），发现循环依赖的模型会被丢弃并 warn
- `ModelWrapper` 内部用 `AtomicReferenceArray` 做 slot 缓存，线程安全地缓存 AO/guiLight/transforms/geometry 等属性的计算结果

**CatFrame** 的 parent 链处理在 [ModelResolver](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/ModelResolver.java) 中：
- 递归解析 parent，合并 textures/elements/display 等字段
- 有 `MAX_DEPTH = 16` 的递归深度限制防止死循环
- 没有循环依赖检测，也没有缓存计算结果——每次 bake 都重新走一遍

### 4. 纹理/材质系统

**高版本** 有完整的材质抽象：
- `Material` / `SpriteId` / `TextureSlots` / `MaterialBaker` — 多层抽象
- 支持**多 Atlas**（block atlas + item atlas），通过 `AtlasManager` 统一管理
- 材质可以在模型烘焙时动态解析（先查 item atlas，找不到再查 block atlas）
- 支持 `forceTranslucent` 等材质属性

**CatFrame** 的纹理处理更直接：
- [VanillaModelManager](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/VanillaModelManager.java) 中手动收集纹理路径 → `TextureStitchEvent.Pre` 注册 → `Post` 时获取 `IIcon`
- block atlas (type 0) 和 item atlas (type 1) 分开处理，但需要手动协调时序（`onTextureStitchPostItem` 修复 item atlas 还没缝合完的问题）

### 5. Blockstate 加载

**高版本** 的 [BlockStateModelLoader](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/BlockStateModelLoader.java)：
- 使用 Mojang 的 `Codec` 序列化框架（`BlockStateModelDispatcher.CODEC`）解析 blockstate JSON
- 支持**资源包堆叠**（`listMatchingResourceStacks`），多个资源包的 blockstate 定义按优先级叠加
- 产出 `Map<BlockState, UnbakedRoot>`，每个 BlockState 都有独立的模型调度

**CatFrame** 的 blockstate 处理在 [BlockstateJson](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/state/BlockstateJson.java) + VanillaModelManager：
- 自己用 Gson 解析，支持 variants 和 multipart
- 通过 `IMetadataMapper` 把 1.7.10 的 metadata 映射到 properties
- 没有资源包堆叠支持
- 用 `buildVariantKey` 手动拼 properties 字符串匹配 variant

### 6. 物品模型加载

**高版本** 有独立的 [ClientItemInfoLoader](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/ClientItemInfoLoader.java)：
- 物品模型有**独立的 JSON 定义文件**（`items/` 目录），用 `ClientItem.CODEC` 解析
- 物品模型和方块模型完全分离，有自己的烘焙流程
- 支持 `ItemDisplayContext` 的完整变换（包括 `ON_SHELF` 等新场景）

**CatFrame** 的物品模型：
- 没有独立的物品模型 JSON，依赖 `model_mappings.json` 或手动注册
- 物品模型复用方块模型的烘焙结果（`ItemModelWrapper` 包装 `BlockStateModel`）
- display transforms 从 `ModelJson.display` 字段获取

### 7. 烘焙（Face Bake）

**高版本** 的 [FaceBakery](file:///d:/GAMES/Minecraft/modss/project/CatFrame/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/resources/model/cuboid/FaceBakery.java)：
- 使用 JOML（`Vector3f`, `Matrix4f`, `Transformation`）做数学运算
- 顶点坐标烘焙时就除以 16 归一化到 0-1 空间
- 支持 `ModelState` 的完整变换矩阵（不只是 x/y 旋转，还有任意的 `Transformation`）
- 有 `recalculateWinding` — 旋转后自动重新计算顶点绕序
- 使用 `Interner` 做顶点/材质去重（内存优化）
- UV 变换支持任意矩阵（不只是 90° 旋转）

**CatFrame** 的 [BlockJsonModelBake](file:///d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/model/BlockJsonModelBake.java)：
- 用原生 `double[]`/`float[]` 数组做数学
- 顶点坐标保持在 0-16 像素空间
- 只支持 x/y 整数角度旋转（`applyXRotation`/`applyYRotation`）
- UV rotation 只支持 0°/90°/180°/270°
- 没有顶点去重优化

### 8. 渲染调度

**高版本**：
- `BlockStateModelSet` / `BlockModelSet` — 按 BlockState 索引的模型集合
- `BlockModelRenderState` — 渲染状态封装
- 模型烘焙结果直接是 `BlockStateModel` 接口，渲染时直接调用
- 有 `modelGroups` 优化——相同模型的 BlockState 共享 group ID，避免不必要的 chunk 重渲染

**CatFrame**：
- 通过 `BlockStateModel` 接口 + `MetadataBlockModel`/`StateBlockModel`/`MultipartBlockModel` 等实现
- 渲染走 `UniformRenderPipeline`，需要手动处理 metadata → model 的映射
- 没有 model group 优化

### 总结一句话

高版本是一个**完全重构的、异步并行的、接口驱动的**现代模型系统；CatFrame 是在 1.7.10 的限制下**手工搭建的等价功能**，核心逻辑一致（JSON 解析 → parent 链 → 纹理收集 → quad 烘焙 → 渲染调度），但在并发、缓存、抽象层次上有明显差距——这也是 1.7.10 引擎限制下的合理取舍。