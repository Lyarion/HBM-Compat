# HBM Compat（HBM 兼容）

为 HBM 流体注册 Forge 流体，支持 AE 总线直接与 HBM 机器进行流体交互

支持 AE 终端用 HBM 储罐倒入 / 导出 HBM 流体，支持 NEI 拖动 HBM 流体图标或使用流体识别码标记 HBM 流体

添加 AE 与 HBM 之间的兼容，支持装配机、化工厂及其大型工厂自动切换配方，推送物品、流体

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

### 与 NTGE Bob Fluid Translator 共存

启动时读取 `config/hbmcompat.cfg`，修改后需要完整重启；客户端与服务端必须使用相同的流体映射模式。

```text
fluids {
    S:fluidMapping=auto
}
client {
    B:hideBobFluidBlocks=false
}
```

- `auto`（默认）：安装 Bob 时调用 Bob 的实际映射，复用其 Forge 流体对象，支持自定义后缀和映射；不会再为已复用类型额外注册无后缀流体。Bob 未安装或某类型没有可用映射时，回退到 HBM-Compat 原名称。
- `legacy`：保留 HBM-Compat 原名称，供已有存档过渡；和 Bob 共存时仍可能出现两份流体、容器与首选输出不一致。它不是“强制 Bob 使用 HBM-Compat”的开关。
- `hideBobFluidBlocks=true`：可选隐藏 NEI 中 Bob 的 `xxx_fluid_block` 物品项，保留 NEI 的正常流体显示项；不删除方块、桶或流体注册，也不改变传输行为。默认关闭，便于诊断。

流体总线、配方编码、样板适配器、HBM 图标及识别码转换共用首选映射。反向查找同时接受**已经存在且没有歧义**的旧名称和 Bob 名称；不会为输入别名凭空创建第二份流体。自定义映射若让两个 HBM 类型共用一个首选 Forge 名称，会明确报错，而不是静默把一种流体当成另一种。

容器桥在 Bob 之后执行，保留已有容器映射，只补缺失项；已有映射与首选流体或容量不同会输出警告。Bob 未安装或关闭自身容器桥时，HBM-Compat 仍可桥接真实容器。HBM-Compat 不接管 Bob 对图标/识别码的容器注册，相关实际灌装、倒出行为需要另外测试。

**旧存档升级：本功能不迁移 ME 库存、流体槽或已编码样板。** 如果存档保存了 `deuterium` 等无后缀名称，添加 Bob 或从 `legacy` 改为 `auto` 前，应先备份，并保持 `legacy` 直到旧库存与样板完成迁移。仅有反向别名不代表 AE 会合并两种库存；新模式未注册的旧名称无法由本模组自动恢复。新测试世界可直接使用 `auto`。

### ME HBM 样板适配器

一个单独的方块，基于 AE2 的 ME 接口架构，将编码后的 AE2 / AE2FC 样板转换为 HBM 机器操作。
支持纯物品、纯流体，以及物品 + 流体的组合样板，并在机器支持时自动切换配方。目前支持七种机器：

| 机器 | 注册名 |
|-----|--------|
| 装配机 / Assembly Machine | `hbm:machine_assembly_machine` |
| 大型装配厂 / Assembly Factory | `hbm:machine_assembly_factory` |
| 化工厂 / Chemical Plant | `hbm:machine_chemical_plant` |
| 大型化工厂 / Chemical Factory | `hbm:machine_chemical_factory` |
| 焊接台 / Soldering Station | `hbm:machine_soldering_station` |
| 电弧焊机 / Arc Welder | `hbm:machine_arc_welder` |
| 矿物酸化器 / Acidizer | `hbm:machine_crystallizer` |

七种机器都是 HBM 的多方块结构，适配器会自动沿着结构找到真正的核心方块，
所以贴在任意一面的外壳上都能工作。

大型装配厂和大型化工厂的四个配方通道会被自动并行调度。每次 AE2 推送只对应一个配方批次，
适配器优先复用已经选择相同配方的空闲通道，再选择输出已清空且可安全切换配方的通道；
连续合成任务会自然分配到其余空闲通道。每个通道仍需放入能够解锁对应配方的蓝图。
为避免 HBM 在切换配方时重置流体罐，带有不兼容残留输出流体的通道不会被重新分配。

矿物酸化器按样板精确数量投放物品与酸液，空罐自动切换为所需流体；同种残液可继续补充，
异种残液、容量不足或流体识别码与样板冲突时拒绝投料，不清空储罐或改动识别码。
自动提取卡只回收产物槽，不提取酸液、电池、容器或升级卡。效率升级留下的输入仍视为待加工，清空后才接收下一批。

适配器支持**样板容量卡**，和 ME 接口一样最多装 3 张，每张多开一行 9 个样板槽，装满为 36 个样板。
和 AE2 原版行为一致：拔掉卡片后，被关闭的那几行里的样板会在打开 GUI 时掉落出来，
同时合成 CPU 对该适配器的能耗也会按卡片数量提高。

样板编码这一侧也做了对接：可以在 NEI 里一键编码 HBM 机器配方（物品 / 流体混合均可），
HBM 流体图标物品也可以直接拖进 AE2 / AE2FC 的幻影槽用于标记。
编码好的样板鼠标悬停会显示对应的 HBM 机器与配方。

### ME HBM 流体输入 / 输出总线

两个总线部件，在 ME 存储与 HBM 储罐之间直接传输 HBM 流体，完整支持 AE2 的速度、容量和红石升级卡。

覆盖范围按 HBM 的 `api.hbm.fluidmk2` 接口判定，流体罐、
Big-Ass Tank、BAT-9000、桶、UF6 / PuF6 罐、油桶等等全部适用，将来 HBM 新增的 MK2 储罐也会自动生效。
上面七种机器的输入 / 输出储罐同样可以接总线；大型工厂的四组配方罐都会被纳入传输。

总线会读取储罐自身的收发模式（`getSendingTanks` / `getReceivingTanks`），
所以把储罐设成只收或只发时，总线会安静待机，而不是跟储罐的 GUI 设置对抗。

### 大型工厂配方分配模式

样板适配器连接大型装配厂或大型化工厂时，界面左侧显示“工厂配方分配”按钮：

- **并行优先（默认）**：同一配方可使用全部四个通道。
- **种类优先**：工厂内已有同配方加工中或待加工通道时，不再重复投料，其余通道可接收不同配方。

模式按适配器保存，重进世界后保留。种类优先检查整座工厂，但其他设为并行优先的适配器仍可重复投料。
切换模式只影响后续投料；加工完成且输入清空后释放配方占用，残留输出仍遵守安全切换检查。
按钮不改变 AE 的配方请求顺序，也不自动回收产物。连接普通机器时隐藏按钮。

### 新版 NEI / NEE 的配方转移

支持 NEE 1.7.42 使用的 NEI `isFluidDisplayItem` 检查：可解析、非加压且数量为正的 HBM 流体图标会被识别为流体显示项，从 NEI 点击“+”转入处理样板时转换为 AE 流体槽。真实容器及流体识别码不被此接口标记为显示项。保留旧版 NEI 接口兼容，不提高现有最低版本要求。

## 已知限制

- 方块材质需要更新
- **自动提取卡**：放入适配器现有四个升级槽之一即可自动回收机器全部物品、流体产物（含副产物和手动加工产物），不要求安装样板或存在合成任务。最多安装一张，可与三张样板容量卡共存，取出即停止。
- 自动回收遵循适配器朝向，覆盖上述七种机器及大型工厂全部通道；只提取输出槽和输出罐。消耗 ME 能量，网络满、缺电或缺频道时未接收的产物留在机器中，恢复后重试；空闲后产出也会自动回收。
- 流体沿用现有映射规则，不回收带压力或无法映射的流体。未安装自动提取卡时，仍需在机器输出侧另装总线 / 管道。
- 自动提取卡暂不提供合成配方，可从创造模式红石物品栏获取，或使用 `/give <玩家> hbmcompat:auto_extract_card`。无需更换 AE2。
- 适配器只按样板输入（物品、流体及精确数量）选择配方，不要求把副产物全部写入样板；同一目标机器上有多条配方匹配输入时仍会拒绝，不能靠输出消除歧义。
- 样板声明的产物仍由 AE 等待回收：少写副产物不会阻止选配方，但写入机器不会产生的产物会导致合成任务等待。副产物也需要安排回收（自动提取卡可一并回收），避免堵塞机器。
- HBM 航天版未测试。

## 调试命令与诊断参数

游戏内执行 `/hbmc debug` 可切换个人聊天调试模式，无需 OP 或开启作弊。开启后显示当前维度正在运行的样板适配器和流体总线诊断（类型、坐标、原因），仅自己可见；重复原因自动去重。再次执行关闭，退出游戏后自动关闭。聊天开关无需重启，也不改变下列 JVM 参数控制的日志输出。

以下启动参数默认关闭：

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
