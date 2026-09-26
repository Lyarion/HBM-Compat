# HBM Compat（HBM 兼容）

Minecraft 1.7.10 的 HBM 与 AE2 兼容模组，让 ME 网络使用 HBM 机器自动合成，并直接存取 HBM 流体。

## 主要功能

- **机器自动合成**：通过 ME HBM 样板适配器投放物品和流体，并在机器支持时自动切换配方。
- **流体传输**：使用 ME HBM 流体输入 / 输出总线连接 HBM 储罐和机器，支持速度、容量及红石升级卡，遵循储罐的收发设置。
- **容器罐装与倒出**：在 ME 终端中直接使用 HBM 流体容器。
- **NEI 配方转移**：点击“+”编码处理样板，也可拖动 HBM 流体图标或使用流体识别码标记流体。

## 样板适配器

将适配器连接 ME 网络并朝向机器，放入处理样板即可使用。支持贴在多方块机器外壳上。

支持机器：**装配机、大型装配厂、化工厂、大型化工厂、焊接台、电弧焊机、矿物酸化器**。
大型工厂支持四通道加工，每个通道仍需放入对应蓝图。矿物酸化器的流体识别码须与样板所需酸液一致。

### 自动提取卡

装入适配器后，自动回收所连接机器的物品、流体产物及副产物，也支持手动加工的产物。最多一张。

### 投料设置

| 设置 | 效果 |
| --- | --- |
| 单批阻挡（默认） | 加工中或仍有输入物品、流体时，等待后再投下一批 |
| 持续补料 | 输入空间足够时，继续追加同配方材料 |
| 并行优先（大型工厂默认） | 同一配方可使用四个通道 |
| 种类优先（大型工厂） | 同配方已有加工或待加工通道时，不再占用新通道 |

补料与工厂分配可分别设置，重进世界后保留。切换只影响后续投料。

## 使用注意

- 样板输入的物品、流体及数量须与配方一致；同一机器有多个相同输入配方时无法区分。
- 输入空间不足或有不兼容残留时不会投料；加压流体不受支持。
- HBM 航天版未测试。

## 可选联动

支持 **NTGE Bob Fluid Translator 2.1.12-NTGE**，默认使用 HBM Compat 自身的流体映射。
`config/hbmcompat.cfg` 中的 `fluidMapping` 可选 `legacy`（默认，使用自身映射）或 `auto`（安装 NTGE Bob Fluid Translator 时复用其映射）；`hideBobFluidBlocks=true` 可隐藏 NEI 中的流体方块项。已有配置保持原值，如需改用自身映射，请将 `fluidMapping` 设为 `legacy`。

修改后需重启，客户端与服务端的映射模式须一致。旧存档切换前请备份；库存和样板不会自动迁移，迁移完成前保持原映射设置。

## 排查问题

输入 `/hbmc debug` 开关聊天诊断，无需 OP；消息仅自己可见，退出游戏后关闭。

## 环境要求

Minecraft **1.7.10**，Forge **10.13.4.1614**。支持以下前置版本：

| 模组 | 支持范围（含两端） |
| --- | --- |
| HBM Nuclear Tech Mod | 1.0.27_X5758 ～ 1.0.27_X5808 |
| Applied Energistics 2 Unofficial | rv3-beta-1024-GTNH ～ rv3-beta-1068-GTNH |
| AE2 Fluid Crafting Rework | 1.5.99-gtnh ～ 1.5.110-gtnh |
| NotEnoughEnergistics | 1.7.38 ～ 1.7.42 |
| NotEnoughItems GTNH | 2.8.114-GTNH ～ 2.8.144-GTNH |

范围内版本未逐一测试，更高版本兼容性待确认。

客户端与服务端均需安装。升级时替换旧版 HBM Compat，不要同时保留多个版本。

## 许可与署名

**GNU 通用公共许可证 v3.0（GPLv3）**

- 流体图标与色块直接读取 HBM 自带资源，详见 [`HBM-Nuclear-Tech-TEXTURES-NOTICE.txt`](src/main/resources/META-INF/licenses/hbmcompat/HBM-Nuclear-Tech-TEXTURES-NOTICE.txt)。
- Forge 流体命名方案参照 Justus0405 的 NTM Fluid Converters。
- 感谢 GTNewHorizons 与 HBM NTM。
