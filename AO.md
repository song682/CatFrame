-Minecraft26.1是怎么计算这个环境光遮蔽的？
-Deep thinking
```The user is asking about how Minecraft 26.1 (the version they're working with) calculates ambient occlusion (环境光遮蔽). Let me search the codebase to find relevant code related to ambient occlusion calculations.

Let me first search memory for any relevant knowledge, and also search the codebase for ambient occlusion related code.
```
**SearchMemory FINISHED**

**SearchCodebase FINISHED**
Deep thinking
```The user is asking about how Minecraft 26.1 calculates ambient occlusion. Let me search more specifically for ambient occlusion related code. In Minecraft, ambient occlusion (AO) in the context of block rendering is typically found in the block rendering/lighting code. Let me look for relevant classes.

In Minecraft, ambient occlusion is typically calculated in:
1. The block model rendering code (like `BlockModelRenderer`, `FaceData`, etc.)
2. The light map / light calculation code

Let me search for more specific terms related to ambient occlusion in rendering.
```
**SearchCodebase FINISHED**

**Grep ERROR**

**Grep ERROR**
Deep thinking
```Let me search for more relevant terms related to ambient occlusion in Minecraft's rendering code. I should look for things like "FaceData", "Light", "Shade", "AO" in the rendering context.
```
**SearchCodebase FINISHED**

**SearchCodebase FINISHED**
Deep thinking
```I found the critical file: `BlockModelLighter.java`. This is the class responsible for ambient occlusion in Minecraft 26.1. Let me read the full file to understand the complete calculation.
```
**Read FINISHED**
Deep thinking
```Now let me also look at the related files to understand the full picture:
1. `ModelBlockRenderer` - which decides when to use ambient occlusion vs flat lighting
2. `CardinalLighting` - which provides the directional shade values
3. `BlockStateBase.getShadeBrightness` - which calculates the shade brightness of a block position

Let me look at these files.
```
**SearchSymbol FINISHED**

**SearchCodebase FINISHED**
Deep thinking
```Now I have a comprehensive understanding of how Minecraft 26.1 calculates ambient occlusion. Let me also check the `CardinalLighting` class for the directional shade values.

Let me summarize what I've found and provide a clear, detailed explanation to the user.

The ambient occlusion calculation in Minecraft 26.1 involves several key components:

1. **BlockModelLighter** - The main class handling AO calculations
2. **ModelBlockRenderer** - Decides whether to use AO or flat lighting
3. **BlockBehaviour.BlockStateBase.getShadeBrightness()** - Gets the shade value for a block position
4. **CardinalLighting** - Provides directional lighting multipliers
5. **LightCoordsUtil** - Handles light coordinate blending

The core flow:

1. `ModelBlockRenderer.tesselateBlock()` decides whether to use AO or flat lighting
2. If AO is used, `BlockModelLighter.prepareQuadAmbientOcclusion()` is called

Let me look at CardinalLighting and LevelRenderer.getLightCoords for the complete picture.
```
**SearchSymbol FINISHED**

**Grep ERROR**
Deep thinking
```Let me search for the CardinalLighting class and the getLightCoords and getShadeBrightness methods in LevelRenderer.
```
**Read FINISHED**

**Glob FINISHED**
Deep thinking
```Let me read the CardinalLighting class and also find the getLightCoords method in LevelRenderer.
```
**Read FINISHED**

**SearchSymbol FINISHED**
Deep thinking
```Now I have all the pieces. Let me also check the `BlockBehaviour.getShadeBrightness` default implementation. I already saw it at line 322-324:

```java
protected float getShadeBrightness(final BlockState state, final BlockGetter level, final BlockPos pos) {
    return state.isCollisionShapeFullBlock(level, pos) ? 0.2F : 1.0F;
}
```

And the `BlockStateBase.getShadeBrightness` at line 457-459:
```java
public float getShadeBrightness(final BlockGetter level, final BlockPos pos) {
    return this.getBlock().getShadeBrightness(this.asState(), level, pos);
}
```

So now I have a complete picture. Let me write up a comprehensive explanation.

The ambient occlusion system in Minecraft 26.1 works as follows:

1. **Entry Point**: `ModelBlockRenderer.tesselateBlock()` decides whether to use AO rendering or flat rendering based on:
    - `ambientOcclusion` setting (from options)
    - `blockState.getLightEmission() == 0` (not an emitter)
    - `part.useAmbientOcclusion()` (the model part supports AO)

2. **The Core AO Calculation** in `BlockModelLighter.prepareQuadAmbientOcclusion()`:

   a. **Shape Analysis**: `prepareQuadShape()` determines the quad's face shape by computing its bounding box min/max X, Y, Z.

   b. **Face Classification**:
    - `faceCubic`: The quad is a full cube face (touching block boundaries)
    - `facePartial`: The quad doesn't fill the entire face (like stairs, slabs)

   c. **Sampling 5+4 Positions**: For a given quad face direction, it samples:
    - 4 corner positions around the block (at the face plane)
    - 4 diagonal corner positions (the corner between two adjacent blocks)
    - Center position

   d. **Fallback for Transparent Corners**: If the block diagonally adjacent to an edge is transparent (not view-blocking), it falls back to reading the block behind the edge corner.

   e. **AO Value Calculation**: For each vertex of the quad:
    - Average the shade (brightness) of the center + 3 relevant corners
    - `lightLevel = (shade3 + shade0 + shadeCorner03 + shadeCenter) * 0.25F`
    - This is the core AO computation

   f. **Weighted Version for Partial Faces**: When `facePartial` is true, each vertex gets its own weighted blend using the `faceShape` values (how far the quad extends in each direction), preventing hard edges on non-full faces.

   g. **Final Scaling**: After per-vertex color is computed, `CardinalLighting.byFace(direction)` multiplies the result for directional shading (down=0.5, up=1.0, north/south=0.8, west/east=0.6).

3. **Shade Brightness Source**: `BlockBehaviour.getShadeBrightness()` returns:
    - 0.2 for full solid blocks (darkening/shadow)
    - 1.0 for transparent blocks (no shadow)

4. **Light Coordinates**: The light coords (sky+block light) are blended similarly using `LightCoordsUtil.smoothBlend()` and `LightCoordsUtil.smoothWeightedBlend()`.

Now I have a comprehensive understanding. Let me write a clear explanation.
```
好的，我已经找到了 Minecraft 26.1 环境光遮蔽的完整计算逻辑。核心实现在 `BlockModelLighter` 类中。下面是详细解析：

---

## 整体架构

```
ModelBlockRenderer          (决定使用AO还是平面光照)
└── BlockModelLighter   (核心AO计算)
├── prepareQuadShape()          → 分析面几何形状
├── prepareQuadAmbientOcclusion() → 计算AO值
├── CardinalLighting            → 方向光照系数
└── LightCoordsUtil             → 光照坐标混合
```


---

## 1. 何时启用AO？

在 [ModelBlockRenderer.tesselateBlock()](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\block\ModelBlockRenderer.java#L65) 中，同时满足三个条件才启用AO：

1. **选项** `ambientOcclusion = true`（玩家设置）
2. **方块不发光** `blockState.getLightEmission() == 0`
3. **模型部分支持AO** `part.useAmbientOcclusion()`

---

## 2. 形状分析 — `prepareQuadShape()`

**[BlockModelLighter.prepareQuadShape()](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\block\BlockModelLighter.java#L216-L271)**

遍历面的4个顶点，计算面的 `minX/maxX/minY/maxY/minZ/maxZ` 包围盒，然后判定两个关键属性：

- **`faceCubic`**：面是否是完全的"满方块面"（即紧贴方块边界 `0` 或 `1`，或者碰撞箱是完整方块）
- **`facePartial`**：面是否是"部分面"（非满块，如楼梯、台阶的侧面）

同时将包围盒数据存入 `faceShape[]` 数组，包括6个方向的值和6个翻转值（`1.0 - value`），后续用于加权计算。

---

## 3. 核心AO计算 — `prepareQuadAmbientOcclusion()`

### 3.1 采样9个位置

对于一个方块面方向，需要读取 **周边9个位置** 的亮度和明暗值，分三类：

**① 与面同一平面的4个相邻方块（corner 0~3）：**

对应 `AdjacencyInfo` 中定义的 4 个方向：

| 面方向 | corners[] |
|--------|-----------|
| DOWN | WEST, EAST, NORTH, SOUTH |
| UP | EAST, WEST, NORTH, SOUTH |
| NORTH | UP, DOWN, EAST, WEST |
| SOUTH | WEST, EAST, DOWN, UP |
| WEST | UP, DOWN, NORTH, SOUTH |
| EAST | DOWN, UP, NORTH, SOUTH |

例如下方面：读取位置（下-西）、（下-东）、（下-北）、（下-南）这4个邻居。

对每个位置获取：
- `lightX` = 光照坐标（天空+方块光照）
- `shadeX` = 明暗值（`getShadeBrightness()`）

**② 4个对角位置（corner 0+2, 0+3, 1+2, 1+3）：**

这里涉及 **透明边角回退逻辑**：如果对角处的方块是透明的（`!cornerX.isViewBlocking()`），则读取更远一格的方块。否则直接复用相邻格的值。

例如 corner02：如果 corner0的对角面 和 corner2的对角面 都是不透明的，则 `shadeCorner02 = shade0`；否则读取 `basePosition + corner0 + corner2` 位置。

**③ 中心位置：**
- 如果是满块面：读取面的外部（`basePosition`）的明暗值，光照取外部
- 否则：读取方块自身中心位置的明暗值和光照

### 3.2 AO值计算（非部分面情况）

四个顶点的AO颜色值分别是相邻三个角与中心的平均值：

```
顶点0 shade = (shade3 + shade0 + shadeCorner03 + shadeCenter) × 0.25
顶点1 shade = (shade2 + shade0 + shadeCorner02 + shadeCenter) × 0.25  
顶点2 shade = (shade2 + shade1 + shadeCorner12 + shadeCenter) × 0.25
顶点3 shade = (shade3 + shade1 + shadeCorner13 + shadeCenter) × 0.25
```


注意顶点顺序经过 `AmbientVertexRemap` 重映射，使顶点索引与 QuadInstance 的顶点顺序一致。

光照坐标也类似混合：
```
顶点0 light = smoothBlend(light3, light0, lightCorner03, lightCenter)
顶点1 light = smoothBlend(light2, light0, lightCorner02, lightCenter)
顶点2 light = smoothBlend(light2, light1, lightCorner12, lightCenter)
顶点3 light = smoothBlend(light3, light1, lightCorner13, lightCenter)
```


### 3.3 AO值计算（部分面情况 — 加权版本）

当 `facePartial && doNonCubicWeight` 时，使用 `faceShape[]` 数组中的几何数据进行 **加权混合**：

每个顶点有8个权重值（成对出现，每对相乘），代表该顶点与四个候选颜色之间的插值关系：

```java
// 以顶点0为例
tempShade1 = (shade3 + shade0 + shadeCorner03 + shadeCenter) × 0.25
tempShade2 = (shade2 + shade0 + shadeCorner02 + shadeCenter) × 0.25  
tempShade3 = (shade2 + shade1 + shadeCorner12 + shadeCenter) × 0.25
tempShade4 = (shade3 + shade1 + shadeCorner13 + shadeCenter) × 0.25

// 权重 = faceShape[a] × faceShape[b]  (a,b对于每个顶点不同)
顶点0最终值 = clamp(tempShade1×w01 + tempShade2×w23 + tempShade3×w45 + tempShade4×w67, 0, 1)
```


例如下方面(DOWN)的顶点0：权重用到 `FLIP_WEST×SOUTH`, `FLIP_WEST×FLIP_SOUTH`, `WEST×FLIP_SOUTH`, `WEST×SOUTH`

这确保了部分面上的AO过渡平滑，不会出现硬边缘。

### 3.4 最终方向光照缩放

AO值计算完成后，还要乘以**方向光照系数** [CardinalLighting](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-common-52430b475d-26.1.2-sources\net\minecraft\world\level\CardinalLighting.java#L7-L9)：

```
默认值: 下=0.5, 上=1.0, 北=0.8, 南=0.8, 西=0.6, 东=0.6
下界值: 下=0.9, 上=0.9, 北=0.8, 南=0.8, 西=0.6, 东=0.6
```


---

## 4. 明暗值来源 — `getShadeBrightness()`

默认实现 [BlockBehaviour](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-common-52430b475d-26.1.2-sources\net\minecraft\world\level\block\state\BlockBehaviour.java#L322-L324)：

```java
protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
    return state.isCollisionShapeFullBlock(level, pos) ? 0.2F : 1.0F;
}
```


- **完整方块 → 0.2**（产生阴影暗角）
- **非完整方块（或透明方块）→ 1.0**（不产生阴影）

个别方块会覆写，如 `MudBlock` 返回 0.2 始终，`StructureVoidBlock`/`TransparentBlock` 返回 1.0。

---

## 5. 光照坐标与混合

[LevelRenderer.getLightCoords()](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\LevelRenderer.java#L1436-L1447) 读取方块位置的天空光和方块光照级，若有发光则叠加。

[LightCoordsUtil.smoothBlend()](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-common-52430b475d-26.1.2-sources\net\minecraft\util\LightCoordsUtil.java#L61-L89) 对四个方向的天空光和方块光分别做简单平均，得到平滑过渡的光照。

---

## 总结

Minecraft 26.1 的环境光遮蔽本质是 **基于相邻方块阻挡的顶点着色**：

1. 对面周围的9个位置采样明暗值（0.2=实心/1.0=透明）
2. 每个顶点的AO = 中心 + 三个相邻角的平均值
3. 部分面用几何权重做更精细的插值混合
4. 再乘以方向光照系数（不同面不同亮度）
5. 光照坐标也做同样的顶点级平滑混合

这样在墙角处（两侧都是实心方块）就会出现明显的暗角，而在开阔区域则更亮，模拟了光线被遮挡的效果。
这样在墙角处（两侧都是实心方块）就会出现明显的暗角，而在开阔区域则更亮，模拟了光线被遮挡的效果。

-那么这个和LightMap是不是共同组成了最终的光照在玩家的世界里是怎么呈现的，对吗？
-Deep thinking
```The user is asking about how ambient occlusion (AO) and the lightmap (光照贴图) work together to create the final lighting appearance in the Minecraft world. Let me search for relevant code/files related to the lightmap system in the client rendering pipeline, particularly in the shader or rendering code that combines these two.

Let me look at the rendering pipeline to understand how AO results and lightmap data are combined.
```
**SearchCodebase FINISHED**

**SearchCodebase FINISHED**
Deep thinking
```Now let me look at the key shader files and the Lightmap class to understand how AO and lightmap combine in the shader pipeline.
```
**Glob FINISHED**

**SearchSymbol FINISHED**

**SearchCodebase FINISHED**
Deep thinking
```Now I have a clear picture. Let me read the actual shader files for block rendering (`block.fsh` and `terrain.fsh`) to understand how AO and lightmap are combined at the shader level.
```
**Read FINISHED**

**Read FINISHED**

**Read FINISHED**

**Read FINISHED**

**Read FINISHED**
Deep thinking
```Now I have the complete picture. Let me also check the ChunkSectionLayer to see what pipeline each layer uses, to confirm the full pipeline flow.
```
**Read FINISHED**

**Read FINISHED**

**Read FINISHED**
Deep thinking
```Now let me also check DefaultVertexFormat.BLOCK to confirm what data format the vertex contains, and check the terrain pipeline definitions.
```
**Read FINISHED**

**Read FINISHED**
Deep thinking
```Now I have the complete picture. Let me look at the GENERIC_BLOCKS_SNIPPET to understand the full shader pipeline stack.
```
现在我已经完全理解了整个渲染流水线。下面给出完整解析：

---

## 完全正确

AO（环境光遮蔽）和 LightMap（光照贴图）是**两个不同的维度**，它们在渲染管线的不同阶段发挥作用，最终**相乘组合**形成玩家看到的光照效果。

### 完整的渲染管线

```
Java端 (CPU)                          →    GLSL着色器 (GPU)
                                            ↓
BlockModelLighter                         Vertex Shader (block.vsh / terrain.vsh)
  ├── 计算每个顶点的Color (AO) ──────────→  in vec4 Color
  └── 计算每个顶点的UV2 (光照坐标) ──────→  in ivec2 UV2
                                                     ↓
                                            vertexColor = Color × sample_lightmap(Sampler2, UV2)
                                                                   ↑          ↑
                                                              AO阴影      LightMap纹理采样
                                                     ↓
                                            Fragment Shader (block.fsh / terrain.fsh)
                                              fragColor = texture(Sampler0, texCoord0) × vertexColor
                                                          ↑                         ↑
                                                      方块纹理图集               AO × LightMap
```


### 两个维度的具体分工

#### ① AO（环境光遮蔽）— 在 `Color` 通道

由 [BlockModelLighter.prepareQuadAmbientOcclusion()](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\block\BlockModelLighter.java#L37-L198) 在 CPU 端计算，写入顶点 `Color` 属性。

- **作用**：模拟**局部微观遮挡**——墙角、凹槽处变暗，开阔处变亮
- **粒度**：每个顶点独立（所以墙角能看到平滑渐变的暗角）
- **范围**：只看**相邻方块**的形状（碰撞箱是否为完整方块）
- **值域**：0.2（完全被遮挡）~ 1.0（完全无遮挡）

#### ② LightMap（光照贴图）— 在 `UV2` 通道

光照坐标是两个 `0~15` 的整数（天空光级和方块光级），编码进顶点 `UV2` 属性，在 GPU 上采样一张 **16×16 的纹理**（[Lightmap](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\Lightmap.java#L20-L48)）：

- **作用**：模拟**全局光照强度**——白天/夜晚/洞穴/发光方块的整体亮度
- **数据来源**：Minecraft 光照引擎（洪水填充算法传播天空光和方块光）
- **纹理内容**：由 [lightmap.fsh](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2\assets\minecraft\shaders\core\lightmap.fsh) 逐帧更新，包含：
    - **天空光颜色和强度**（随时间/天气变化）
    - **方块光颜色和强度**（带闪烁和色调）
    - **环境光颜色**
    - **夜视效果**、**黑暗效果**、**亮度(gamma)设置**
    - **Boss黑暗效果**

### 组合公式

在顶点着色器（[terrain.vsh](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2\assets\minecraft\shaders\core\terrain.vsh#L27) / [block.vsh](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2\assets\minecraft\shaders\core\block.vsh#L26)）中：

```glsl
vertexColor = Color * sample_lightmap(Sampler2, UV2);
```


即：**最终颜色 = AO值(AO明暗) × LightMap值(场景光照)**

在片段着色器（[terrain.fsh](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2\assets\minecraft\shaders\core\terrain.fsh#L89)）中：

```glsl
vec4 color = texture(Sampler0, texCoord0) * vertexColor;
```


即：**最终像素 = 方块纹理 × (AO × LightMap)**

### 完整图示

```
              Minecraft 方块最终像素颜色
                         │
                         ▼
               ┌─────────────────┐
               │  方块纹理(Sampler0) │
               └────────┬────────┘
                        ×
               ┌────────┴────────┐
               │   vertexColor    │
               └────────┬────────┘
                        ×
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
  AO(顶点明暗)     LightMap强度       CardinalLighting
  ──────────    ──────────────      ──────────────
  局部微遮挡       全局光照强度         方向明暗
  (CPU计算)     (GPU纹理采样)       (面方向系数)
  0.2~1.0       0~15级光照          下0.5/上1.0
                                        侧0.6~0.8
```


### 举几个直观的例子

| 场景 | AO贡献 | LightMap贡献 | 最终效果 |
|------|--------|-------------|---------|
| 白天草地中央 | `1.0`(开阔) × | 天空光15级 | 明亮 |
| 墙角凹槽 | `0.2`(被遮挡) × | 天空光15级 | 墙角出现暗角 |
| 洞穴深处 | `1.0`(开阔) × | 方块光0级 | 漆黑 |
| 洞穴火炬旁凹槽 | `0.2`(被遮挡) × | 方块光14级 | 暗角但可见 |
| 下界 | × `0.9`(方向光均匀) × | 光照信息 | 整体明亮均匀 |

所以是的，**AO 和 LightMap 共同组成了玩家世界的最终光照呈现**——AO 负责微观的几何遮挡细节，LightMap 负责宏观的场景光照环境，两者在 GPU 上逐顶点相乘，互不冲突，相辅相成。

-你在看看不要只是奉承我。仔细地看看，因为我是人会出错，你有幻觉，也会出错
-Deep thinking
```用户让我仔细检查之前说的内容，不要奉承，因为AI会产生幻觉，人也可能出错。让我重新认真审视一下整个 AO + LightMap 的渲染管线，确保没有说错或遗漏重要细节。

让我重新核实几个关键点：

1. **BlockModelLighter 中 AO 的 `Color` 值** - 我前面说 AO 范围是 0.2~1.0，实际上 `getShadeBrightness()` 默认返回 0.2 或 1.0，但 AO 计算时是 4 个值的平均值，所以实际范围可以是 0.2~1.0。这里没问题。

2. **`CardinalLighting` 的缩放** - 在第 196-197 行有 `outputInstance.scaleColor(cardinalLighting.byFace(direction))`，也就是说 AO 颜色还要再乘以方向光照系数(0.5~1.0)。所以实际最终 AO 颜色范围是 0.1~1.0。我之前在表格里提到"方向明暗"但没在公式中明确体现这个乘法是在 CPU 端完成的而不是在 shader 里。

3. **`sample_lightmap` 函数** - 采样 UV 计算：`(uv / 256.0) + 0.5 / 16.0`，其中 uv 是 ivec2（整数向量），范围 0~15。`uv / 256.0` 将整数转换为 0/256~15/256 的浮点，加上 0.5/16 = 0.03125，然后 clamp 到 0.5/16~15.5/16。这实际上是让纹理坐标对准每个像素的中心。这个没问题。

4. **Vertex Format BLOCK** 包含：Position, Color, UV0, UV2。其中 UV2 就是 lightmap 坐标。这个我在上一轮说了。

5. **AO 写入的是 Color 还是需要乘以 CardinalLighting？** - 看第 182-193 行，AO 计算的结果是 `ARGB.gray(lightLevel)` 写入 `outputInstance.setColor()`，然后第 196-197 行 `outputInstance.scaleColor(cardinalLighting.byFace(direction))` 缩放。所以最终写入顶点缓冲区的 Color 已经是 AO × CardinalLighting 的结果了。

6. **shader 中的 vertexColor = Color × sample_lightmap(Sampler2, UV2)** - 这个没错，`Color` 已经包含了 AO × CardinalLighting，再乘以 lightmap 采样的结果。

等等，让我再想想我之前的表述有没有问题：

我说"AO值(AO明暗)"和"LightMap值(场景光照)"乘在一起。但实际上 `Color` 顶点属性已经包含了 `CardinalLighting` 方向光照。所以更准确的公式是：

`vertexColor = (AO × CardinalLighting) × LightMap`

我之前说的是：
```
AO(顶点明暗) × LightMap强度 × CardinalLighting(方向明暗)
```

这其实也是对的，乘法交换律。但在表格里我画成了三个独立的乘数，而实际上 CardinalLighting 是在 CPU 端乘到 Color 里的，shader 中只有一步乘法。

让我再想想还有什么遗漏...

其实还有一个重要的点我没提：**对于没有 AO 的方块（平面光照模式）**，在 `prepareQuadFlat()` 中：
- `Color = ARGB.gray(cardinalLighting.byFace(direction))` — 只有方向光照，没有 AO 暗角
- LightMap 还是正常采样

所以平面模式和 AO 模式的差别就在于 Color 通道是否包含 AO 信息。

另外，我之前说"AO 范围 0.2~1.0"不够精确。经过 `CardinalLighting` 缩放后，实际范围是：
- 下方面：0.2 × 0.5 = 0.1 ~ 1.0 × 0.5 = 0.5
- 上方面：0.2 × 1.0 = 0.2 ~ 1.0 × 1.0 = 1.0
- 侧面：0.2 × 0.6~0.8 = 0.12~0.16 ~ 1.0 × 0.6~0.8 = 0.6~0.8

让我再看看有没有其他问题...

还有一个：BlockModelLighter 的 `translateAO` 方法... 实际上在 Weighted 版本中，AO 计算更复杂，用到了 `faceShape[]` 数组的几何权重。我之前确实提到了，但可能不够深入。

还有一个可能出错的地方 - 我看看 lightmap 的 UV 到底是什么。

从 sample_lightmap.glsl：
```glsl
vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}
```

等等，这个 UV 是 `ivec2`，而在 DefaultVertexFormat.BLOCK 中 UV2 应该是 `vec2`（两个 float），但 vertex shader 中用了 `ivec2 UV2` 来接收。这就意味着数据在传入 GPU 时被解释为整数向量了。

lightmap 的 u 分量存储 block light level (0-15)，v 分量存储 sky light level (0-15)。

但是从 `LevelRenderer.getLightCoords()` 来看，它返回的是一个 `int`，用 `LightCoordsUtil.pack(block, sky)` 打包的。pack 格式是 `block << 4 | sky << 20`。

在写入顶点缓冲时，这个 int 被写入 UV2 属性，然后在 vertex shader 中作为 `ivec2` 接收。由于 UV2 是 vec2 格式（两个 float），一个 int 被拆成两个 short/ushort 分量，然后各自被解释为 float，再在 shader 中被 ivec2 接收。

嗯，这个转换过程比较复杂。但实际上这不是关键，关键是我之前说的整体流程是对的。

让我想想还有什么可能不准确的地方...

嗯，我可能犯了一个错误——我在上一轮画了一个结构图，说：

```
AO(顶点明暗)     LightMap强度       CardinalLighting(方向明暗)
0.2~1.0         0~15级光照          下0.5/上1.0/侧0.6~0.8
```

这三个是并列的乘数关系，但实际在代码中：

1. AO 值计算时已经包含了 `shadeCenter`、`shade0~shade3`、`shadeCornerXX` 四个值的平均
2. 然后写入 Color（灰度值）
3. 然后 `scaleColor(cardinalLighting.byFace(direction))` — 乘以方向系数
4. 然后在 shader 中 `vertexColor = Color × sample_lightmap(Sampler2, UV2)`
5. 最后在 fragment shader 中 `texture(Sampler0, texCoord0) × vertexColor`

所以更准确的公式链是：
```
最终颜色 = 方块纹理 × [(shade0~3平均) × CardinalLighting(direction)] × LightMap(block, sky)
```

嗯，我之前在表格里把 CardinalLighting 单独列出来作为一个乘数，在结构上没问题但可能让人误解为它在 shader 中乘的。实际上它在 CPU 端就乘到 Color 里了。

好，让我总结一下需要修正/补充的地方，然后回复用户。
```
你说得对，让我重新严格审视每一处细节，挑出可能的错误和遗漏。


**Read FINISHED**

**Read FINISHED**

**Read ERROR**
Deep thinking
```Let me check the QuadInstance to understand how setColor and scaleColor work, and also look at the lightmap.fsh to understand the lightmap texture format more carefully.
```
**SearchSymbol FINISHED**

**Read FINISHED**
Deep thinking
```Now let me also check how the quad is actually written to the vertex buffer (the QuadInstance → vertex buffer pipeline) to make sure I understand how the vertex data flows.

Let me also check the ARGB.gray and ARGB.scaleRGB functions.
```
**SearchSymbol FINISHED**
Deep thinking
```Now I have a very thorough understanding. Let me check the LightCoordsUtil more carefully to understand the UV2 format.

From earlier:
```java
public static int pack(final int block, final int sky) {
    return block << 4 | sky << 20;
}
```

So light coords is packed as: bits 4-7 = block light (4 bits, 0-15), bits 20-23 = sky light (4 bits, 0-15).

In the vertex shader, `UV2` is declared as `ivec2` (integer vec2). But in the vertex format `DefaultVertexFormat.BLOCK`, UV2 has two float components.

Wait - the vertex format says `UV2` is a `vec2` (two floats), but in the shader it's received as `ivec2`. This means the int is written to the vertex buffer and reinterpreted on the GPU side. Let me check how the BufferBuilder writes the light coords.

Actually, let me look at how a QuadInstance is written to the buffer.
```
**SearchSymbol FINISHED**
Deep thinking
```Let me search for where QuadInstance is serialized to buffer.
```
**SearchCodebase FINISHED**
Deep thinking
```Let me now check how the QuadInstance's colors and lightCoords get written to the actual vertex buffer. I need to find where the vertex data is assembled from the QuadInstance.
```
**SearchCodebase FINISHED**
Deep thinking
```Now I have a very comprehensive understanding. Let me look at `putPackedUv` to understand how the light coords int is written to the vertex buffer.

I see in the BufferBuilder code:
```java
public VertexConsumer setLight(final int packedLightCoords) {
    long pointer = this.beginElement(VertexFormatElement.UV2);
    if (pointer != -1L) {
        putPackedUv(pointer, packedLightCoords);
    }
    return this;
}
```

And the direct write path:
```java
putPackedUv(lightStart + 0L, lightCoords);
```

So `putPackedUv` writes the packed light coords. For the BLOCK format, UV2 is a vec2 (2 floats = 8 bytes). So `putPackedUv` must write the int in a way that it becomes two shorts packed as two floats when read by the GPU.

Let me search for `putPackedUv`.

Actually I think I understand enough now. Let me see if there are actual errors in what I previously said.

Looking more carefully at the rendering pipeline:

1. **AO in CPU**: `prepareQuadAmbientOcclusion()` computes AO values per vertex → writes to `color0~color3` via `setColor(vertex, ARGB.gray(lightLevel))` → then `scaleColor(cardinalLighting.byFace(direction))` multiplies all color channels by the directional factor.

2. **Light coords in CPU**: Same method computes light coords per vertex → writes to `lightCoords0~lightCoords3` via `setLightCoords(vertex, packedLight)`.

3. **Vertex buffer write**: The `QuadInstance` data is serialized into the vertex buffer. For BLOCK format: Position(xyz floats), Color(ARGB int), UV0(uv floats), UV2(packed light coords int).

4. **Vertex shader**: `Color` (in vec4) receives the ARGB color, `UV2` (in ivec2) receives the light coords reinterpreted as two shorts.

5. `vertexColor = Color * sample_lightmap(Sampler2, UV2)` — multiplies the Color (which contains AO × CardinalLighting) by the lightmap texture lookup result.

6. **Fragment shader**: `fragColor = texture(Sampler0, texCoord0) * vertexColor` — multiplies the block texture by the vertex color.

OK, so let me think about what I got wrong or could be misleading:

**Potentially misleading things in my previous answer**:

1. **我画了三个独立乘数**：AO、LightMap、CardinalLighting 三个并列。虽然结果上乘法交换律三者是等价的，但从代码层面，CardinalLighting 是在 CPU 端作为 AO 的最后一步操作的（`scaleColor`），shader 中只有一步 `Color × sample_lightmap`。我的表述可能让人误以为 GPU 做了两次乘法。

2. **"AO范围0.2~1.0"**：这个表述有歧义。`getShadeBrightness()` 返回 0.2 或 1.0，但 AO 值是 4 个 shade 值的平均。另外 `scaleColor(CardinalLighting)` 还会再缩小一次。所以最终写入 Color 通道的值范围是 0.1(CardinalLighting.DOWN × 0.2/4的平均) ~ 1.0(CardinalLighting.UP × 1.0)。

3. **表格中"0~15级光照"**：LightMap 采样返回的是 vec4（RGBA），不是标量。它包含了环境光、天空光颜色、方块光颜色等综合计算结果，不是简单的"亮度值"。

4. **我当时说"AO 值(AO明暗)"**——实际上 Color 通道在写入时为每个顶点设置了不同的灰度值。但 AO mode 下，四个顶点可以有不同的 Color 值（因为每个顶点独立计算），而 flat mode 下一个面所有顶点 Color 都一样。我没强调这个区别。

5. **LightCoords 也是每个顶点独立**，和 Color 一样。在 AO mode 下四个顶点可能有不同的 lightCoords（通过 smoothBlend），而在 flat mode 下整个面用相同的光照坐标。我没有在之前的解释中明确说明 lightCoords 的顶点级变化也有平滑作用。

6. **我说的"AO 只看相邻方块的形状"**不完整。AO 计算时除了看同层的4个邻居，还要看**对角邻居**的遮挡情况（透明回退逻辑），还看**当前方块自身**和**面的外方块**。实际采样了9个位置。

7. **一个更重要的遗漏**：`faceCubic` 影响 lightCenter 和 shadeCenter 的采样位置。当 faceCubic=true 时，光照和阴影取的是**面外侧的方块**（如上面块的上面），否则取**方块自身中心**。这个差异我没说。

8. **关于 LightMap 的更新频率**：LightMap 并不是逐帧更新的！`LightmapRenderStateExtractor.tick()` 设置了 `needsUpdate = true`，但 `Lightmap.render()` 只在 `needsUpdate` 为 true 时才重绘纹理。`tick()` 每帧都调，所以 LightMap 确实是每帧更新没错。但需要注意 LightMap 是逐像素着色器计算出来的（用 screenquad + lightmap.fsh），不是从某个文件中加载的。

好吧，用户说得很对，让我更诚实地审视自己的回答，修正不准确之处。
```
你说得对，我重新逐行对照了代码，发现之前的回答确实有几处不准确和遗漏。我来纠正：

---

## 需要修正的地方

### 错误1：渲染公式的表述有误导

我之前说：
```
最终颜色 = AO × LightMap × CardinalLighting
```


这**三个乘数并列**的图示会让读者以为 GPU 上做了两次乘法。实际上 CPU 端已经先完成了 `AO × CardinalLighting`，写入顶点 `Color` 属性，shader 中只有一步乘法：

```glsl
// vertex shader 中
vertexColor = Color * sample_lightmap(Sampler2, UV2);
//               ↑                    ↑
//        AO × CardinalLighting    LightMap 纹理采样
```


`CardinalLighting` 是在 [BlockModelLighter 第 196-197 行](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\block\BlockModelLighter.java#L196-L197)通过 `scaleColor()` 直接在 Color 整数上做 RGB 缩放，不是 GPU 着色器做的。

### 错误2：Color 通道实际包含的信息比我说的多

我说"AO值范围 0.2~1.0"，这不对。Color 通道最终包含**三层叠加**：

1. `getShadeBrightness()` 原始值：0.2（完整方块）或 1.0（非完整）
2. 4 个 shade 值平均（AO 核心计算）：范围 0.2~1.0
3. `scaleColor(CardinalLighting.byFace(direction))` 再乘一次：下面 ×0.5，上面 ×1.0，侧面 ×0.6~0.8

所以实际写进 Color 的灰度值范围是 **0.1（阴角下面）到 1.0（开阔上面）**，不是 0.2~1.0。

### 错误3：LightMap 不是简单的"亮度值"

我之前说 LightMap 是"0~15级光照"，但这只是 UV2 坐标编码的光照级。`sample_lightmap()` 从 16×16 纹理中采样得到的是一个 **vec4 RGBA 颜色向量**，它已经包含了：

- 环境光颜色
- 天空光颜色 × 天空光强度
- 方块光颜色 × 方块光强度（带色调渐变和闪烁）
- 夜视效果叠加
- 黑暗效果衰减
- 亮度(gamma)调节
- Boss 战黑暗叠加

所有这些在 [lightmap.fsh](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2\assets\minecraft\shaders\core\lightmap.fsh) 中计算后写入纹理。vertex shader 采出来的实际上是**最终的场景光照颜色**。

### 错误4：轻描淡写了 AO 中心位置的选取逻辑

我说"中心位置"时没说明不同情况下的差异：

| `faceCubic` | lightCenter 来源 | shadeCenter 来源 |
|---|---|---|
| `true`（满块面） | 面的外部方块（`basePosition`） | 面的外部方块 |
| `false`（非满块面） | 方块自身（`centerPosition`），但如果面外侧的方块不透明则取外侧 | 方块自身 |

这个在 [第 117-126 行](D:\GAMES\Minecraft\modss\project\fabric-example-mod-26.1.2\minecraft-clientOnly-52430b475d-26.1.2-sources\net\minecraft\client\renderer\block\BlockModelLighter.java#L117-L126)：

```java
int lightCenter = this.cache.getLightCoords(state, level, centerPosition);
pos.setWithOffset(centerPosition, direction);
BlockState nextState = level.getBlockState(pos);
if (this.faceCubic || !nextState.isSolidRender()) {
    lightCenter = this.cache.getLightCoords(nextState, level, pos);  // 取外部
}

float shadeCenter = this.faceCubic
    ? this.cache.getShadeBrightness(level.getBlockState(basePosition), level, basePosition)  // 外部
    : this.cache.getShadeBrightness(level.getBlockState(centerPosition), level, centerPosition);  // 自身
```


对于完整方块（如石头），shadeCenter 取的是**面外相邻方块**的亮度，这是合理的——如果你站在石头上面，石头顶面的光照应该取决于头顶上的亮度。而对非完整方块（如草径），shadeCenter 取自**方块自身**。

### 错误5：没强调 LightMap 和 AO 的 lightCoords 平滑也是独立的

在 AO mode 下，**四个顶点的光照坐标可以不同**（通过 `smoothBlend` / `smoothWeightedBlend` 混合），这造成了光照在方块面上的平滑渐变。而在 flat mode 下一个面的所有顶点光照坐标相同。这个我没提，但实际上这是 AO 开启时视觉平滑的一个重要因素。

---

总结：我之前把公式过度简化成了三个并列乘数，但其实更准确的表述是：

**CPU 端（Java）**：
```
每个顶点的 Color = clamp(avg(shade0, shade1, shade2, shade3, shadeCenter)) × CardinalLighting(direction)
每个顶点的 UV2  = smoothBlend(light0, light1, light2, light3, lightCenter)
```


**GPU 端（着色器）**：
```
vertexColor = Color × sample_lightmap(Sampler2, UV2)
fragColor   = texture(Sampler0, texCoord0) × vertexColor
```


总共只有两次链式乘法，没有第三个独立项。CardinalLighting 是作为 AO 的一部分在 CPU 端提前乘好的。谢谢指正。