# QingCharms

Minecraft 护符装备插件，兼容 Spigot/Bukkit `1.8 - 1.12.2`。

QingCharms 只负责护符识别、装备 GUI、槽位限制、玩家数据持久化，以及把已装备的完整 `ItemStack` 交给 ItemLoreStats（ILS）。它不负责属性计算或战斗结算。

## 职责边界

| 职责 | QingCharms | ILS |
| --- | --- | --- |
| 装备、替换、卸下 | 负责 | 不负责 |
| 自定义槽位与装备限制 | 负责 | 不负责 |
| 玩家装备数据持久化 | 负责 | 不负责 |
| 完整物品及 Lore 传递 | 负责 | 读取 |
| 攻击、生命、吸血、暴击等属性 | 不参与 | 根据 Lore 计算 |
| 被动效果与技能等级 | 不参与 | 根据 Lore 处理 |
| 战斗结算 | 不参与 | 负责 |

固定规则：

1. QingCharms 不解析 Lore 中的属性数值，不实现攻击、生命、吸血、暴击、技能等级或战斗结算。
2. 已装备道具始终以完整 `ItemStack` 保存和传递；除配置的成长激活 Lore 会在装备时替换外，其他 Lore 不会被裁剪。
3. ILS 读取完整 Lore 后自行计算属性、效果触发和技能等级。
4. `attributes` 配置节点不属于战斗系统。旧节点最多用于 `/qc list` 显示字段名，不会参与属性传递或计算。
5. 升级、强化、突破如需改变道具，只能修改该道具 Lore，再交给 ILS；不得在 QingCharms 中另建属性成长或结算规则。

## 游戏性约束

- 护符只能在装备 GUI 中装备或替换。
- 背包护符右键、`Shift+右键` 只执行装备或替换，不触发技能。
- 不提供快捷栏施法、施法键、主动释放按钮或其他额外按键。
- 道具效果只通过装备、攻击、受击等事件被动生效，由 ILS 根据 Lore 处理。
- 玩家装备护符后自然获得 ILS 读取到的效果，不需要额外操作。

## 装备反馈

反馈仅用于界面体验，不参与装备状态、持久化、Lore、属性或战斗逻辑。

```text
CharmFeedbackManager
├── 读取 feedback 配置
├── 播放音效
├── 生成粒子
└── 发送简短提示
```

`GuiManager` 仅在成功装备、成功替换、成功卸下后调用：

```java
feedbackManager.play(player, charm, "equip");
```

失败反馈使用 `"failure"`，并且只有 `feedback.play-failures: true` 时才会播放。

### 全局配置

```yaml
feedback:
  play-failures: false
  equip:
    sound: LEVEL_UP
    volume: 1.0
    pitch: 1.0
    particles: FLAME
    count: 12
    radius: 0.4
    message: "&a已装备 {charm}"
  replace:
    sound: LEVEL_UP
    particles: FLAME
    count: 12
    message: "&a已替换为 {charm}"
  unequip:
    sound: CLICK
    particles: SMOKE
    count: 8
  failure:
    enabled: false
    sound: VILLAGER_NO
    particles: SMOKE
    count: 4
    message: ""
```

### 单个护符配置

`feedback.charms.<护符ID>` 中只写需要覆盖的字段，未写的字段继续使用全局默认值：

```yaml
feedback:
  charms:
    example:
      equip:
        particles: LAVA_POP
        count: 16
        message: "&a青玉护符已装备"
      unequip:
        sound: CLICK
        pitch: 0.8
```

消息支持 `{charm}`（等同 `{name}`）、`{name}`、`{id}`、`{type}` 和 `{event}`。`{charm}`/`{name}` 优先取物品本身的 display-name（玩家实际看到的名字，含觉醒等运行时改名），物品没有 display-name 时才退回护符配置的 `display-name`，最后才使用护符 ID。音量、音调、数量和半径会限制在兼容且安全的范围内。

实现约束：

- 音效使用 Spigot 1.8 的 `Sound` API。
- 粒子使用旧版 `Effect`/`World#playEffect` API，兼容 1.8-1.12.2。
- 反馈异常会被隔离和记录，不会回滚或中断装备、替换、卸下及保存。
- 触发点只放在 `GuiManager` 成功或失败业务逻辑之后，不进入 ILS、Lore 或属性逻辑。
- 装备反馈与护符触发提示相互独立，触发提示使用 Tick 节流。
## 护符触发提示

护符实际效果由 ILS 或其他 RPG 插件触发。效果触发后，外部插件调用：

```java
charmAPI.notifyTrigger(player, charmId, "on-hit");
```

QingCharms 只执行：

1. 按玩家 UUID、护符 ID、触发类型检查 Tick 阈值。
2. 格式化简短文本。
3. 通过普通聊天消息发送一次。

```text
TriggerMessageService
├── 玩家 UUID
├── 护符 ID
├── 触发类型
└── 最后显示 Tick
```

判断条件：

```text
currentTick - lastShownTick >= cooldownTicks
```

### 全局配置

```yaml
trigger-message:
  enabled: true
  cooldown-ticks: 5
  format: "&7[护符] &f{name}已触发"
```

### 按护符覆盖

```yaml
charms:
  example:
    trigger-message:
      cooldown-ticks: 10
      format: "&7[青玉护符] &f效果已触发"
```

未覆盖的字段继续使用全局值。格式支持 `{name}`、`{charm}`、`{id}`、`{type}` 和 `{trigger}`。

节流只抑制后续提示，不取消或修改 ILS 的实际效果。提示使用普通聊天消息以兼容 1.8-1.12.2，不增加 ActionBar、BossBar、技能状态图标、技能可用提示、护盾、连击、能量或额外战斗状态显示。
## 护符背包

护符背包与装备 GUI 完全分离：

- 装备 GUI：装备、替换、卸下。
- 护符背包：存储、分类和排序。
- 两个 GUI 使用不同的 InventoryHolder 和点击处理器。

```text
CharmBagManager
├── BagStorage
├── BagGuiManager
├── BagSorter
├── BagCategoryFilter
└── CharmMetadataManager
```

### 命令

```text
/qc bag
/qingcharmsbag
/qcb
/qc open <player>  # 管理员
```

### 存储与唯一 ID

- 存入时优先复用配置中的实例 UUID；没有配置身份时生成新的 UUID。
- 唯一 ID 不从物品内容推导，因此相同外观的两件护符也不会共用身份。
- 数据保存到 `charm-bags/<玩家UUID>.yml`。
- 护符背包下方的普通背包保持原版左键拿起、右键放下；Shift+左键/右键可快速存入，也可拿住护符后点击背包空格手动放入。`/qc bag` 背包内左键护符可取出。
- “存入全部护符”按钮会逐件处理可识别护符。
- 存入或取出会先保存候选数据；操作失败时恢复背包和数据状态，不主动删除道具。

### 分类和排序

护符支持展示元数据：

```yaml
charms:
  example:
    category: 项链
    level: 1
    source-description: "由深渊守卫掉落，或在青玉商店兑换。"
```

- 分类支持全部、头盔、胸甲、护腿、靴子、项链、天空和自定义分类。
- 支持按名称、等级、稀有度、类型排序。
- 不提供搜索框。

`source-description` 只用于 GUI 展示，不实现掉落、制作、合成、碎片或分解。

### 点击行为

| 操作 | 默认行为 |
| --- | --- |
| 背包条目左键 | 取出 |
| 普通背包左键 / 右键 | 原版拿起 / 放下，可立即再次放置 |
| 普通背包 Shift+左键 / Shift+右键 | 单件快速存入 |
| 光标拿住护符后左键 / 右键背包空格 | 手动放入背包 |
| 普通背包内拖拽 | 使用原版拖拽，不会影响背包展示区 |

### 容量和分页

```yaml
charm-bag:
  enabled: true
  title: "&8青玉护符背包"
  rows: 6
  page-size: 45
  capacity: 180
  permission: qingcharms.bag
  sort:
    default: rarity
```

`page-size` 是每页护符格数，不能超过 `(rows - 1) * 9`；`capacity` 是玩家护符背包总容量。

当前不实现搜索、碎片、制作配方、分解或掉落系统。
配置文件分工：

- `config.yml`：全局设置、反馈、护符定义和装备条件。
- `gui.yml`：装备 GUI 与护符背包布局、槽位、权限和点击配置。
- `classes.yml`：职业（`classes`）与构筑规则（`rules`），插件启动时自动生成。

旧版 `config.yml` 中的 `gui`、`charm-bag` 节点会在启动时自动迁移到 `gui.yml`，随后从 `config.yml` 移除；`classes`、`rules` 节点同样会在启动时迁移到 `classes.yml` 并从 `config.yml` 移除。`config.yml` 不再管理职业与构筑。

## 构建与安装

```powershell
mvn clean package
```

构建产物：

```text
target/QingCharms-0.1.0.jar
```

安装步骤：

1. 将 JAR 放入服务器 `plugins` 文件夹。
2. 启动服务器生成 `config.yml`、`classes.yml`、`gui.yml`、`language_zh.yml`、`qcItems/`、`playerdata/` 和备份目录。
3. 在 `config.yml` 配置护符，在 `classes.yml` 配置职业与构筑规则，在 `gui.yml` 配置装备 GUI 和护符背包，然后执行 `/qc reload`。
4. 如需属性与被动效果，安装并配置 ILS。

### 护符认证与防伪造

默认启用 `settings.require-charm-authentication: true`。QingCharms 会为 `/qc give` 发出的每件护符写入不可见的实例 ID 和服务端 HMAC 签名；装备、背包、成长、构筑和 API 识别都先验证签名，再进行材质、名字和 Lore 匹配。仅复制外观的 MMOItems 物品、创造模式自定义物品或其他伪造装不会被识别。

`charm-auth-secret` 首次启动时自动生成并持久化。请备份但不要公开该密钥；更换密钥会使旧签名失效。升级已有存档时，未签名的旧物品默认不再识别，应通过 `/qc give` 重新发放。仅在受控迁移期间可临时设置 `require-charm-authentication: false`，此模式会恢复旧的纯外观识别，不具备防伪造能力。

## 护符来源

### `config.yml`

```yaml
charms:
  example:
    enabled: true
    material: 95
    data: 14
    display-name: "&a青玉护符"
    lore:
      - "&7项链护符"
      - "&c攻击: &r+100"
    type: 项链
    rarity: COMMON
    permission: qingcharms.charm.example
```

`lore` 中的属性由 ILS 解释。QingCharms 只保存该 Lore。

### `qcItems`

`qcItems` 支持嵌套目录，文件名去掉扩展名后作为护符 ID。文件需要包含 Bukkit 可序列化的 `Item` 或 `item`：

```yaml
Item:
  ==: org.bukkit.inventory.ItemStack
  v: 1631
  type: GOLD_INGOT
  amount: 1
  meta:
    ==: ItemMeta
    meta-type: UNSPECIFIC
    display-name: "&a青玉护符"
    lore:
      - "&7项链护符"
      - "&c攻击: &r+100"

charm:
  enabled: true
  type: 项链
  rarity: LEGENDARY
  permission: qingcharms.charm.A
```

说明：

- 同 ID 时，`config.yml` 中的定义优先。
- `qcItems` 护符保存完整 `Item` 元数据，适合直接使用 ILS 导出的物品。
- 物品没有 `display-name` 时不校验名字，只按材质、`data`（若配置）和 Lore 识别；`display-name` 仅用于 `/qc list`、提示消息和护符背包的显示名。
- **`data` 以 `Item` 内的 `damage`/`data` 为准**：`qcItems` 文件的 `Item` 块已经描述了自己的变体（玻璃/羊毛颜色等），因此 `charm.data` 在 `qcItems` 中不会覆盖它；两者不一致时只打一条 warning 并以 `Item` 为准。需要区分变体就写在 `Item` 里，不要写 `charm.data`。
- 文件名即护符 ID，可以用中文（如 `阴间护符.yml`），签名与识别都按原样处理。
- 所有战斗属性、效果和技能信息都应写入 `Item` 的 Lore。
- 旧版 `attributes` 节点仅可在 `/qc list` 中显示字段名，不加载为属性模型。

### 用 `/qc export` 生成护符文件

用 `/lore` 或其它工具在游戏里做好物品后，手持它执行：

```text
/qc export <id> [类型] [-f]
```

QingCharms 会：

1. 剥离物品上的旧实例签名，把干净的 `Item` 写入 `qcItems/<id>.yml`；
2. 写入 `charm.type`：显式写了类型就用写的，否则按 `gui.yml` 中 `equip`/`unequip` 槽位的 `type` 列表，在物品显示名和 Lore 中逐行匹配，命中即写入；
3. 把手中物品替换为带服务端签名的实例，可直接右键装备，不再需要额外 `/qc give`。

`-f` 覆盖已存在的同名文件，改完 Lore 重新导出时使用。

两个前提：

- 物品 Lore 必须包含该类型文字，或在该文件中配置 `equip-lore` 指向某一行；否则仍会被装备 Lore 校验拦下，导出时提示 `type-not-in-lore`。
- `qcItems` 文件缺少 `type` 且无法从 Lore 推断时，护符仍会被加载，但只能存储、无法装备；启动日志会给出 warning。

如果文件是手工放入的且没有 `type`，加载时同样会尝试从 Lore 推断；推断不到才保持 `NONE`。

## 装备 GUI

执行：

```text
/qc gui
/qingcharmsgui
/qcg
```

交互：

1. 右键背包护符：空槽直接装备；已有护符时提示槽位已满。
2. `Shift+右键` 背包护符：空槽提示无替换物品；已有护符时直接替换，旧护符返回背包。
3. 左键已装备的护符槽执行卸下，不再需要先选中背包物品。
4. 仍兼容单独的 `action: unequip` 槽位。
5. 背包空间不足时不会执行替换或卸下。

`gui.yml` 中的装备 GUI 示例：

```yaml
gui:
  # rows 或 row：1-6 行。
  row: 2
  title: "&8青玉护符装备"
  permission: qingcharms.gui
  slots:
    # 槽位名称自由；slot/position 指定位置，也可直接用数字作为名称。
    necklace:
      slot: 10
      id: 98
      id_data: 0
      name: "&a项链饰品槽"
      lore:
        - "&7左键卸下 | 背包右键装备 | Shift+右键替换"
      type: 项链
      action: equip
    unequip-necklace:
      slot: 16
      id: 160
      id_data: 14
      name: "&c卸下护符"
      type: 项链
      action: unequip
    close:
      slot: 17
      id: 160
      id_data: 14
      name: "&c关闭 GUI"
      action: close
```

`action` 支持：

| 动作 | 行为 |
| --- | --- |
| `equip` | 左键槽位卸下；背包护符右键装备，`Shift+右键`替换 |
| `unequip` | 卸下并放回背包 |
| `refresh` / `reopen` | 刷新 GUI |
| `close` | 关闭 GUI |
| `none` | 不执行操作，仅展示图标 |

`action` 为 `equip` 或 `unequip` 时，`type`（或 `slot-id`）是必填项，且不能为空或 `NONE`。
缺失类型的槽位会在加载时被跳过（既不显示也不可点击），因此无法用于装备饰品。
写成其它未知动作时槽位仍会显示，但点击无反应，日志会给出提示。

装备条件：

- 护符定义来自 `qcItems`。
- 护符 `type` 与 GUI 槽位 `type` 一致。
- 物品 Lore 包含该 `type` 或配置的匹配 Lore。
- 玩家拥有护符和槽位权限。

原版护甲槽、拖拽、数字快捷键和世界右键均不能装备 QingCharms 护符。

## 持久化

玩家数据保存在：

```text
playerdata/<UUID>.yml
```

示例：

```yaml
players:
  00000000-0000-0000-0000-000000000000:
    charms:
      example: true
    equipped:
      necklace: <完整 ItemStack>
    item-ids:
      "equipped:necklace":
        unique-id: 550e8400-e29b-41d4-a716-446655440000
        fingerprint: 3204dbca-2161-3bb5-88c0-53271493d87c
    progression:
      550e8400-e29b-41d4-a716-446655440000:
        unique-id: 550e8400-e29b-41d4-a716-446655440000
        level: 3
```

持久化原则：

- `equipped` 保存完整 `ItemStack`，包括名称、Lore、附魔和其他 ItemMeta。
- 道具实例 UUID 保存在 `item-ids` 和 `progression` 配置节点，不写入玩家可见 Lore。
- QingCharms 只重写成长系统管理的 Lore，不解析或替换 ILS 属性 Lore。
- 更改装备会更新数据版本，ILS 可据此刷新读取结果。
- 数据写入采用临时文件、同步和原子替换，并支持定时备份。

## ILS 桥接

QingCharms 向 ILS 提供完整装备物品：

```java
EquipmentSnapshot snapshot = api.getEquipmentSnapshot(player);
List<ItemStack> completeItems = snapshot.getItems();
```

兼容入口：

```java
List<ItemStack> completeItems =
        ((QingCharmsPlugin) plugin).getEquippedCharmItems(player);
```

ILS 的 `GearStats` 读取这些 `ItemStack` 的完整 Lore，并自行计算属性。QingCharms 不读取、拆分或汇总属性值。

后续物品变化规则：

- 升级、强化、突破只修改物品 Lore。
- 修改后继续使用完整 `ItemStack` 保存和传递。
- 属性公式、触发概率、技能等级和战斗效果始终由 ILS 根据 Lore 决定。

## 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/qc help` | `qingcharms.command` | 帮助 |
| `/qc list` | `qingcharms.command` | 查看护符 |
| `/qc gui` | `qingcharms.gui` | 打开装备 GUI |
| `/qc bag` | `qingcharms.bag` | 打开自己的护符背包 |
| `/qc open <player>` | `qingcharms.command.admin` | 查看并管理在线玩家的护符背包 |
| `/qc reload` | `qingcharms.command.reload` | 重载配置 |
| `/qc add` | `qingcharms.command.admin` | 添加护符 |
| `/qc set` | `qingcharms.command.admin` | 修改护符 |
| `/qc remove` | `qingcharms.command.admin` | 删除护符 |
| `/qc on` / `/qc off` | `qingcharms.command.admin` | 启用或禁用 |
| `/qc give` | `qingcharms.command.give` | 发放护符 |
| `/qc export` | `qingcharms.command.admin` | 导出手持物品到 `qcItems`，自动识别类型并签名 |
| `/qc check` | `qingcharms.command` | 检查手持物品为什么装不上：签名状态、匹配结果、同材质护符逐项对比 |

## 配置节点

### `settings`

```yaml
settings:
  player-save-interval-seconds: 30
  player-backup-interval-minutes: 60
  player-backup-retention: 7
  player-backup-folder: DataBacked
  qc-items-folder: qcItems
```

### `charms`

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否加载 |
| `material` / `data` | 物品材质和 Data/Durability |
| `item` / `item-file` | 完整物品或 ILS 导出的物品 YAML |
| `display-name` | 匹配用显示名 |
| `lore` | 匹配用 Lore；实际属性也由 ILS 从 Lore 读取 |
| `type` | GUI 槽位类型 |
| `equip-lore` | 装备所需 Lore 行 |
| `rarity` | 稀有度 |
| `permission` | 使用权限 |
| `requirements` | 装备限制：`player-level` 玩家等级、`classes` 职业 ID 列表 |
| `attributes` | 可选旧版展示标签；不参与属性计算 |
| `trigger-message` | 可选的按护符提示覆盖：`enabled`、`cooldown-ticks`、`format` |

### `language_zh.yml`

所有消息从语言文件读取，支持 `&` 颜色代码和 `{0}`、`{1}` 占位符。

## API

API 合约版本：`1.0.0`。新接口自 `0.1.0` 起提供；旧入口继续保留以兼容现有插件。

### 查询接口

```java
CharmQueryAPI query = ((QingCharmsPlugin) plugin).getAPI();

Optional<CharmDefinition> charm = query.getCharm(charmId);
boolean charmItem = query.isCharm(item);
String uniqueId = query.getUniqueId(item);
ItemStack equipped = query.getEquippedItem(player, slotId);
List<ItemStack> equippedCharms = query.getEquippedCharms(player);
Set<String> owned = query.getOwnedCharmIds(player);
```

查询接口只返回副本或不可变集合，不修改道具、Lore 或玩家数据，也不计算 ILS 属性。`getUniqueId()` 优先读取物品上的实例身份（含隐藏 Lore 标记），兼容旧物品 Lore 中的 `Unique ID`、`UUID` 或 `Item ID`；没有实例标记且没有配置上下文时才回退到稳定的内容 ID。

### 操作接口

```java
CharmOperationAPI operations = ((QingCharmsPlugin) plugin).getAPI();

boolean equipped = operations.equip(player, item, slotId);
boolean removed = operations.unequip(player, slotId);
boolean notified = operations.notifyTrigger(player, charmId, triggerType);
```

操作接口只允许在 Bukkit 主线程调用。

`notifyTrigger()` 使用 `TriggerMessageService` 执行 Tick 节流，并在允许显示时触发 `CharmTriggerNotifyEvent`、发送一次普通聊天消息。返回 `true` 仅表示提示已显示；它不计算属性、触发战斗效果或执行技能。

### Bukkit 事件

| 分类 | 事件 |
| --- | --- |
| 装备 | `CharmEquipEvent`、`CharmUnequipEvent`、`CharmReplaceEvent` |
| 构筑 | `CharmBuildChangeEvent`、`CharmRuleActivateEvent`、`CharmRuleDeactivateEvent` |
| 成长 | `CharmLevelEvent`、`CharmEnhanceEvent`、`CharmBreakthroughEvent`、`CharmStarEvent`、`CharmAwakeningEvent`、`CharmProgressionChangeEvent` |
| 提示 | `CharmTriggerNotifyEvent` |

事件均使用标准 `HandlerList`，并由 QingCharms 在主线程触发。装备、强化、突破、升星和觉醒事件在数据和资源真正变化前触发且可以取消。装备事件携带道具唯一 ID、护符 ID、槽位 ID、旧道具和新道具；成长事件携带旧状态、新状态和消耗资源。

暂不提供 `CharmDropEvent`、`CharmLoadoutSwitchEvent`、`CharmEffectCalculateEvent`，因为 QingCharms 不实现掉落、构筑预设切换或 ILS 属性计算。

兼容入口仍可使用：

```java
CharmAPI api = ((QingCharmsPlugin) plugin).getAPI();
EquipmentSnapshot snapshot = api.getEquipmentSnapshot(player);
List<ItemStack> completeEquipment = snapshot.getItems();
```

API 边界：

- `CharmDefinition` 用于身份、类型和装备约束。
- `EquipmentSnapshot` 只传递完整装备物品和缓存版本。
- QingCharms 只保存和传递最终 Lore；属性求和、战斗计算、效果触发和技能执行始终属于 ILS 或其他插件。
- 后续 API 修改只增加默认方法或重载，不删除既有方法和事件构造器。
## 常见问题

### 护符无法装备

1. 是否来自 `qcItems`。
2. 护符 `type` 是否与 GUI 槽位一致。
3. Lore 是否包含 `type` 或所需 `equip-lore`。
4. 玩家是否拥有护符及槽位权限。
5. 是否通过右键或 `Shift+右键` 操作。

### 修改 Lore 后属性没有变化

1. 修改的是 `Item` 中的实际 Lore。
2. QingCharms 保存的完整物品包含新 Lore。
3. ILS 已安装并能读取 QingCharms 桥接。
4. ILS 对应属性名称和数值格式配置正确。

### `attributes` 中的数值为什么不生效

这是预期行为。`attributes` 不属于战斗系统；所有属性必须写入物品 Lore，由 ILS 读取和计算。

## 当前状态

- 护符注册、发放和查询；
- 自定义 GUI 装备、替换和卸下；
- 原版护甲槽及非 GUI 装备限制；
- 完整 `ItemStack` 与 Lore 持久化；
- 向 ILS 传递完整装备物品；
- 异步保存、原子写入和定时备份；
- 中文提示与配置热重载；
- 不包含 QingCharms 自有属性计算或战斗结算。
---

## 职业构筑（羁绊）

QingCharms 只负责构筑条件解析和最终 Lore 编译。所有属性仍由 ItemLoreStats（ILS）读取最终物品 Lore 后计算，QingCharms 不计算属性，也不实现装备主动技能。

### 架构

| 组件 | 职责 |
|---|---|
| `ClassResolver` | 只通过配置的职业权限节点调用 `player.hasPermission()` 解析职业 |
| `TagResolver` | 统计整个 GUI：每件已装备饰品给职业的每个标签各计 1 次 |
| `RuleResolver` | 解析构筑规则（只有 `bond` 一种算法，配置里不再写 `type`） |
| `BuildResolver` | 组合职业、标签、规则并处理装备校验与构建变更 |
| `LoreCompiler` | 清除旧动态 Lore，并生成独立的构筑效果 Lore 物品供 ILS 读取，不写入玩家装备 |
| `BuildSnapshot` | 固化当次构筑结果：职业、标签计数、激活规则 |

### 标签只有一个来源

构筑标签**只**来自 `classes.yml` 的 `classes.<id>.tags`。饰品（`config.yml` 的 `charms.*` 与 `qcItems` 文件）不再支持 `tags`；老配置里写了会在控制台提示并忽略。

计数方式是「统计整个 GUI 内已装备的饰品」：

- GUI 里每件已装备饰品，都会把当前职业的每个标签各计 1 次。
- 圣骑士 `tags: [圣光]` 装了 3 件 → `圣光 = 3`。
- 圣骑士 `tags: [圣光, 守护]` 装了 3 件 → `圣光 = 3`、`守护 = 3`。
- 一件都没装 → 所有计数为 0，构筑不成立。
- 玩家同时拥有多个职业权限时，每个职业的标签都会计入（同一标签会叠加）。

### 道具限制

`config.yml` 中的 `charms` 与 `qcItems` YAML 使用统一的 `requirements` 字段：

```yaml
requirements:
  player-level: 30

  classes: [paladin]
```

`player-level` 只表示 Minecraft 玩家等级，不表示护符成长等级。`classes` 使用职业 ID；列出多个职业时满足任意一个即可。玩家可以同时拥有多个职业。

### 职业权限映射

以下内容位于 `classes.yml`（`config.yml` 不再管理）：

```yaml
classes:
  paladin:
    name: "圣骑士"
    permission: "deluxetags.tag.sqs"
    tags: [圣光, 守护]
    recommended-tags: [圣光, 守护, 治疗]   # 只用于 /qc build 推荐提示
    titles: ["黎明守卫"]                   # 可选，纯展示

  warrior:
    name: "战士"
    permission: "deluxetags.tag.warrior"
    tags: [力量, 壁垒]
```

GroupManager 负责分配权限，DeluxeTags 使用相同权限节点。QingCharms 只调用 `player.hasPermission(permission)`；不读取或匹配称号名称字符串。多个职业权限可以同时生效。

### 构筑规则

`rules` 下只有一种规则：`bond`（羁绊），因此**不需要写 `type`**。

```yaml
rules:
  holy-bond:
    name: "圣光祝福"          # /qc build 里「已激活羁绊」显示的名字
    tags: [圣光]
    count: 2
    add-lore:
      - "&f圣光祝福: +10%"

  holy-guard:
    name: "圣光守护"
    tags: [圣光, 守护]
    count: 4
    add-lore:
      - "&f防御: +5%"

  paladin-build:
    name: "黎明守卫"
    classes: [paladin]
    tags: [守护]
    count: 3
    add-lore:
      - "&f生命: +5%"
```

判定：`tags` 中所有标签的构筑计数**相加** `>= count` 即激活。

**档位只亮最高的一档。** `tags` 组合相同的规则互相竞争，只有 `count` 最高的那条生效：

```yaml
rules:
  holy-bond:        # count: 2
  holy-bond-pro:    # count: 5，同样是 tags: [圣光]
```

装 2~4 件 → 只亮 `holy-bond`；装 5 件以上 → 只亮 `holy-bond-pro`，低档不会一起叠。
`count` 相同的规则算同档并列，会一起生效（例如同为 2 件的「防御」和「攻击」）。
想手动把不同写法的规则归到一档，就给它们写同一个 `group`。

| 字段 | 说明 |
|---|---|
| `name` | 可选。展示名，`/qc build` 的「已激活羁绊」用它；不写则回退成规则 ID |
| `group` | 可选。档位分组名；不写则按 `tags` 组合自动分组 |
| `tags` | 参与计算的标签，取职业 `tags` 里的名字 |
| `count` | 需要的计数总和，默认 2 |
| `classes` / `class` | 可选。限定只有这些职业能触发；不写则不限职业 |
| `requirements` | 可选。给单个标签设最低数量，见下 |
| `add-lore` | 必填。激活后编译进构筑 Lore 物品的最终属性行 |

`count` 怎么估：

- 单标签：`count` 就等于「需要装备几件」。`tags: [圣光] count: 2` = 装 2 件。
- 多标签：每件饰品会给每个标签各 +1，所以 `tags: [圣光, 守护] count: 4` 也是「装 2 件」。

`requirements` 支持单标签的最低数量：

```yaml
requirements:
  圣光: 3
  守护: 1
```

`add-lore` 必须直接使用 ILS 能识别的最终 Lore 格式，例如 `&f攻击力: +10%`。每个激活规则只生成一次；相同文本会跨规则去重，并通过独立的构筑效果 Lore 物品交给 ILS，不会写入或转移在玩家装备上。动态 Lore 不追加隐藏颜色码。

### 旧的 type / 冲突 / 协同

`type`、`synergy`、`class`、`conflict`、`mode`、`suppress-rules` 都已移除：

- 写了 `type` 的规则会在控制台提示，但规则本身仍按 `bond` 生效。
- 写了 `mode` / `suppress-rules` 的会被忽略并提示。
- 冲突机制（`block` / `suppress`）已整体下线，装备不再互相排斥；`Shift + 右键` 依旧是「替换已占用槽位」。

### API 事件

装备、构筑、成长和提示事件统一列在 [API](#api) 章节。事件只报告状态变化或提示请求，不执行主动技能，也不改变 ILS 的属性计算职责。

## GUI 装备交互

| 操作 | 空槽 | 已占用槽位 |
|---|---|---|
| 右键 | 装备 | 提示：`&c当前槽位已满，按 Shift+右键替换。` |
| `Shift+右键` 背包护符 | 替换 | 空槽提示无替换物品；满槽先把原道具放回背包，再替换；背包不足则回滚 |
| 左键装备槽 | 卸下 | 将已装备护符放回背包 |

所有装备入口统一在修改数据前检查：

`服务端签名有效 -> 道具外观有效 -> 槽位类型匹配 -> Lore 匹配 -> 玩家等级满足 -> 职业权限满足 -> 执行装备`

道具可以配置：

```yaml
requirements:
  player-level: 30

  classes: [paladin]
```

该顺序覆盖背包护符右键装备、Shift+右键替换、从护符背包取出后的装备尝试、管理员发放后的装备尝试，以及 `/qc reload` 对已装备道具的重新校验。提示可在 `language_zh.yml` 的 `charm.equip.level`、`charm.equip.class` 和 `charm.equip.class-any` 中修改。

不实现世界、PVE/PVP、副本、Boss 战或主动技能限制。

`/qc build` 会显示当前职业名（取自 `classes.<id>.name`，支持颜色代码）、契合特质（`recommended-tags`）和已激活羁绊规则 ID，只做展示，不会自动装备。未提供 Build 预设、一键装备/卸下或快速切换功能。

## 护符成长系统

### 配置与类结构

成长配置独立保存在 [progression.yml](D:\Code\Minecraft\PyTsING\Plugins\QingCharms\src\main\resources\progression.yml)，避免与装备、构筑规则重复：

- `ProgressionConfig`：读取成长 Lore 激活条件、等级、强化、突破、星级、觉醒、资源消耗和反馈配置。
- `ProgressionRecord`：按道具唯一 ID 保存等级、强化、突破、星级、觉醒、击杀数量、累计佩戴时间。
- `ResourceService`：校验并扣除佩戴时间、击杀数量、原版经验、Vault 金币、PlayerPoints 点券。
- `ProgressionLoreCompiler`：删除激活 Lore，清理旧成长 Lore，在原位置生成最终成长 Lore，并保留其他静态 ILS Lore。
- `ProgressionService`：执行手动升级、强化、突破、升星和觉醒。
- `ProgressionListener`：记录怪物击杀和在线佩戴时间。

### 成长状态与 Lore

只有 Lore 命中 `progression.activation-lore` 的道具才会启用成长系统。配置支持多个检测文字，按去除颜色后的 Lore 文本包含匹配：

```yaml
progression:
  activation-lore:
    - "可成长"
```

可把检测文字改为 `可升级`、`潜力`、`生长型` 等，也可以同时配置多个。装备时命中的检测 Lore 会被删除，并在原位置替换为当前成长系统 Lore；未命中且没有已有成长状态的道具完全不进入成长系统，保留物品本身的静态 Lore，不生成成长状态，也不累计佩戴时间或击杀资源。

每件成长道具首次绑定时生成唯一 ID，并写入物品的隐藏 Lore 标记及 `playerdata/<玩家UUID>.yml` 的 `item-ids` 与 `progression` 节点。标记不显示文字，玩家不可见；为了让物品提示框不多出空行，隐藏标记会追加在**最后一行可见 Lore 的末尾**（物品完全没有 Lore 时才会单独占一行）；旧 Lore 中的 ID 会被迁移并清理。

成长状态只写入 Lore：

```text
&7等级: 3/10
&7强化: +3
&7突破: I阶段
&7星级: ★★★
&7觉醒: 已觉醒
```

达到条件后解锁的 `unlock-lore` 由 `ProgressionLoreCompiler` 生成。除被命中的成长激活 Lore 外，编译器不会替换原始 ILS Lore；成长、构筑和静态 Lore 的相同文本会去重，避免 ILS 重复计算。动态 Lore 不追加隐藏颜色码。已经启用成长的道具在刷新 Lore 时保持成长状态，不依赖激活 Lore 再次出现。

### 手动命令

```text
/qingcharmsgrowth status [槽位|hand]
/qingcharmsgrowth level <槽位|hand>
/qingcharmsgrowth enhance <槽位|hand>
/qingcharmsgrowth breakthrough <槽位|hand>
/qingcharmsgrowth star <槽位|hand>
/qingcharmsgrowth awaken <槽位|hand>
```

也可以使用别名 `/qcgrow` 或 `/qcprogress`。

升级、强化、突破、升星和觉醒全部由玩家手动执行，不会自动升级或自动强化。突破要求当前强化达到当前上限；升星可要求指定突破阶段；觉醒是一次性操作。

### 资源来源

`wear-seconds` 和 `kills` 是每件道具的累计成长资源；`experience`、`vault`、`playerpoints` 在操作成功时从玩家账户扣除。资源可以在 `level.cost`、`enhance.cost`、突破/升星/觉醒阶段的 `cost` 中组合配置。

### 数据与兼容性

- 每件道具按实例唯一 ID 保存成长状态，不以护符 ID 或物品内容共享进度。
- 旧物品 Lore 中的唯一 ID 会迁移到实例标记并从可见 Lore 清理；成长等级、强化、突破、星级和觉醒状态继续保留。
- 玩家退出、替换、卸下和服务器关闭会保存数据；现有 YAML 保存重试和备份机制继续生效。
- 成长事件：`CharmLevelEvent`、`CharmEnhanceEvent`、`CharmBreakthroughEvent`、`CharmStarEvent`、`CharmAwakeningEvent`、`CharmProgressionChangeEvent`。
- QingCharms 不计算攻击、生命、防御、暴击等属性；这些数值仍由 ItemLoreStats 读取最终 Lore 计算。











