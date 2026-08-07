# HBM Compat（HBM 兼容）

为 HBM 流体注册 Forge 流体，支持 AE 总线直接与 HBM 机器进行流体交互

支持 AE 终端用 HBM 储罐倒入 / 导出 HBM 流体，支持 NEI 拖动 HBM 流体图标或使用流体识别码标记 HBM 流体

添加 AE 与 HBM 之间的兼容，支持装配机、化工厂自动切换配方，推送物品、流体

AI 开发（~~AI 还是太好用了你们知道吗~~），不足之处敬请谅解，经生存档测试，保证基础可用性。

**（以下 AI 生成）**

一个 Minecraft 1.7.10 模组，为 **HBM 核科技模组（HBM Nuclear Tech Mod）** 与
**Applied Energistics 2**、**AE2 流体合成（AE2 Fluid Crafting）**、**NotEnoughEnergistics**
和 **NEI** 之间搭建桥梁。它让 ME 网络能够借助 HBM 机器自动合成，并在 ME 存储与 HBM 流体之间双向搬运。

以上均为本模组的前置依赖，缺一不可，详见 [环境要求](#环境要求)。

## 功能

### 将 HBM 流体注册为 Forge 流体

HBM 流体会被自动注册为 Forge 流体，使用 HBM jar 中自带的 GUI 色块与着色。

### HBM 流体容器支持在 AE 终端罐装 / 倒出

HBM 的流体容器（罐 / 气罐 / 流体桶 / 铅罐 / 单元 / 粒子容器等）会被镜像注册进 Forge 的
`FluidContainerRegistry`，因此可以直接在 **ME 终端**里罐装和倒出：

- 空容器 + 网络中有该流体 → 罐装成满容器
- 满容器 → 倒出流体进网络，返还对应的空容器

由于走的是 Forge 通用接口，同一套映射对 AE2FC 的流体自动填充机、流体转换监视器，
以及其它任何读 Forge 容器注册表的模组同样生效。

两类条目会被刻意跳过：

- **不可逆的单向条目**（石油矿 `ore_oil`、水银锭 `ingot_mercury`、片麻岩气 `ore_gneiss_gas`）。
  HBM 里没有对应空容器，而 Forge 的空容器缺省路径会代入一个桶，倒出时等于凭空产生桶。
- **Forge 已经注册过的条目**（水桶、岩浆桶、水瓶）。HBM 会重复声明这些，跳过以保留 Forge 原有映射。

如需整体关闭这套桥接，可加启动参数 `-Dhbmcompat.noContainerBridge=true`。

### ME HBM 样板适配器

一个单独的方块，基于 AE2 的 ME 接口架构，将编码后的 AE2 / AE2FC 样板转换为 HBM 机器操作。
支持纯物品、纯流体，以及物品 + 流体的组合样板，并在机器支持时自动切换配方。目前支持四种机器：

| 机器 | 注册名 |
|-----|--------|
| 装配机 / Assembly Machine | `hbm:machine_assembly_machine` |
| 化工厂 / Chemical Plant | `hbm:machine_chemical_plant` |
| 焊接台 / Soldering Station | `hbm:machine_soldering_station` |
| 电弧焊机 / Arc Welder | `hbm:machine_arc_welder` |

四种机器都是 HBM 的多方块结构，适配器会自动沿着结构找到真正的核心方块，
所以贴在任意一面的外壳上都能工作。

样板编码这一侧也做了对接：可以在 NEI 里一键编码 HBM 机器配方（物品 / 流体混合均可），
HBM 流体图标物品也可以直接拖进 AE2 / AE2FC 的幻影槽用于标记。
编码好的样板鼠标悬停会显示对应的 HBM 机器与配方。

### ME HBM 流体输入 / 输出总线

两个总线部件，在 ME 存储与 HBM 储罐之间直接传输 HBM 流体，完整支持 AE2 的速度、容量和红石升级卡。

覆盖范围按 HBM 的 `api.hbm.fluidmk2` 接口判定，流体罐、
Big-Ass Tank、BAT-9000、桶、UF6 / PuF6 罐、油桶等等全部适用，将来 HBM 新增的 MK2 储罐也会自动生效。
上面四种机器的输入 / 输出储罐同样可以接总线。

总线会读取储罐自身的收发模式（`getSendingTanks` / `getReceivingTanks`），
所以把储罐设成只收或只发时，总线会安静待机，而不是跟储罐的 GUI 设置对抗。

## 已知限制

- 方块材质需要更新
- **适配器不回收产物**，需要在机器输出侧另装总线 / 管道把产物抽走。
- 当一个样板同时匹配到多种机器的配方时，适配器会判定为歧义并跳过，不做处理。
- HBM 航天版未测试。

## 诊断参数

都是启动参数，默认全关，开启后才会往日志输出，平时完全无开销。

| 参数 | 作用 |
|-----|------|
| `-Dhbmcompat.debugAdapter=true` | 样板适配器推送诊断，配方推不进机器时打印原因 |
| `-Dhbmcompat.debugBus=true` | 流体总线传输诊断 |
| `-Dhbmcompat.noContainerBridge=true` | 关闭 HBM 流体容器到 Forge 的镜像注册 |

## 环境要求

本附属模组针对以下版本编译并运行：

| 模组 | 版本 |
|-----|---------|
| [HBM Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT) | 1.0.27 X5758 |
| [NotEnoughEnergistics](https://github.com/GTNewHorizons/NotEnoughEnergistics) | 1.7.38 |
| [NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems) | 2.8.114-GTNH |
| [Applied Energistics 2 (Unofficial)](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial) | rv3-beta-1024-GTNH |
| [AE2 Fluid Crafting (Rework)](https://github.com/GTNewHorizons/AE2FluidCraft-Rework) | 1.5.99-gtnh |

以上是构建时锁定的版本，其它版本未经测试。

## 构建

1. 将 HBM 发行版 jar 放入 `libs/` 目录（有且仅有一个 `HBM-NTM-*.jar`，多于一个会直接报错）。
2. 执行构建：

   ```bash
   ./gradlew build
   ```

构建产物输出到 `build/libs/`。本项目采用 GTNH 的 Gradle 约定，需要 JDK 17 或更高版本，
Gradle 由 wrapper 固定在 8.13，用 `./gradlew` 即可，不需要单独装 Gradle。

## 许可与署名

**GNU 通用公共许可证 v3.0（GPLv3）**

- 流体图标与色块直接读取 HBM 自带资源，详见 [`HBM-Nuclear-Tech-TEXTURES-NOTICE.txt`](src/main/resources/META-INF/licenses/hbmcompat/HBM-Nuclear-Tech-TEXTURES-NOTICE.txt)。
- Forge 流体命名方案参照 Justus0405 的 NTM Fluid Converters。
- 感谢 GTNewHorizons 与 HBM NTM。
