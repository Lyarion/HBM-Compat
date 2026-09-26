# HBM Compat 1.1.0

## 更新内容

- 新增大型装配厂、大型化工厂和矿物酸化器支持。
- 大型工厂支持四通道加工，可选择“并行优先”或“种类优先”。
- 新增“持续补料”，可在加工中追加同配方材料；默认仍为“单批阻挡”。
- 新增自动提取卡，自动回收机器的物品、流体产物及副产物。合成：同一行放置基础卡、HBM 液压活塞、湮灭核心。
- 样板不再要求写全副产物，输入物品、流体及数量仍须与配方一致。
- 改善新版 NEI/NEE 的 HBM 流体配方转移，支持 NTGE Bob Fluid Translator 联动。
- 更新适配器和自动提取卡材质，增加投料方向箭头。
- 新增 `/hbmc debug`，用于查看聊天诊断。

## 前置支持范围

| 模组 | 支持范围（含两端） |
| --- | --- |
| HBM Nuclear Tech Mod | 1.0.27_X5758 ～ 1.0.27_X5808 |
| Applied Energistics 2 Unofficial | rv3-beta-1024-GTNH ～ rv3-beta-1068-GTNH |
| AE2 Fluid Crafting Rework | 1.5.99-gtnh ～ 1.5.110-gtnh |
| NotEnoughEnergistics | 1.7.38 ～ 1.7.42 |
| NotEnoughItems GTNH | 2.8.114-GTNH ～ 2.8.144-GTNH |

范围内版本未逐一测试，更高版本兼容性待确认。

可选联动：**NTGE Bob Fluid Translator 2.1.12-NTGE**。默认使用 HBM Compat 自身的流体映射（`fluidMapping=legacy`），可设为 `auto` 复用其映射；已有配置保持原值。

## 升级注意

- 客户端与服务端一同替换旧版，安装 `HBM-Compat-1.1.0.jar`。
- 旧存档切换流体映射前请备份；库存和样板不会自动迁移，迁移完成前保持原映射设置。
- 副产物仍需回收；样板填写机器不会产生的产物会让 AE 一直等待。
- HBM 航天版未测试。
