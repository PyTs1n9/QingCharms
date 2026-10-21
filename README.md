# QingCharms青玉

Minecraft 饰品护符插件，兼容 Spigot/Bukkit `1.8 - 1.12.2`，当前版本 `0.2.0`。

> 插件显示名 **QingCharms青玉**；`plugin.yml` 内部的 `name` 保持 `QingCharms`（Bukkit 只接受字母数字和 `-`/`_`，且该值决定数据目录 `plugins/QingCharms/`）。

QingCharms 只负责**护符识别、装备 GUI、槽位限制、玩家数据持久化**，以及把已装备的完整 `ItemStack` 交给 ItemLoreStats（ILS）。它不计算属性、不结算战斗、不实现主动技能。

---

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
2. 已装备道具始终以完整 `ItemStack` 保存和传递；除配置的成长激活 Lore 会在装备时被替换外，其他 Lore 不会被裁剪。
3. ILS 读取完整 Lore 后自行计算属性、效果触发和技能等级。
4. `attributes` 配置节点不属于战斗系统，最多用于 `/qc list` 显示字段名。
5. 等级、强化、突破、升星、觉醒只能修改道具 Lore，再交给 ILS；不得在 QingCharms 中另建属性成长或结算规则。

## 游戏性约束

- 护符只能在装备 GUI 中装备或替换，原版护甲槽、拖拽、数字快捷键、世界右键都不能装备护符。
- 背包护符右键 = 装备，`Shift+右键` = 替换当前槽位；不触发技能。
- 不提供快捷栏施法、施法键、主动释放按钮或其他额外按键。
- 效果只通过装备、攻击、受击等事件被动生效，由 ILS 根据 Lore 处理。

---

## 安装

```powershell
mvn clean package
```

产物：`target/QingCharms-0.2.0.jar`

1. 把 JAR 放进服务器 `plugins/`。
2. 启动服务器，自动生成 `config.yml`、`classes.yml`、`gui.yml`、`progression.yml`、`language_zh.yml`、`qcItems/`、`playerdata/`、`DataBacked/`。
3. 编辑配置后执行 `/qc reload`。
4. 需要属性与被动效果时，安装并配置 ILS。

### 文件分工

| 文件 | 内容 |
| --- | --- |
| `config.yml` | 全局设置、触发提示、装备反馈、护符定义（`charms`） |
| `gui.yml` | 装备 GUI 的行数、标题、槽位、权限 |
| `classes.yml` | 职业（`classes`）与构筑规则（`rules`） |
| `progression.yml` | 护符成长系统 |
| `language_zh.yml` | 全部提示文本 |
| `qcItems/*.yml` | 逐件护符的物品定义（可由 `/qc export` 生成） |
| `playerdata/<UUID>.yml` | 玩家装备与成长数据 |
| `DataBacked/` | 定时备份 |

> 老版本 `config.yml` 里的 `gui`、`classes`、`rules` 节点会在首次启动时自动迁移到 `gui.yml` / `classes.yml` 并从 `config.yml` 移除。`charm-bag` 段已不再被读取，留在配置里也不会生效。

---

## 护符制作流程

推荐工作流只有四步，不提供在游戏内增删改护符的命令：

```text
游戏里用 /lore 等方式做好物品
        ↓
   /qc export <id>       把手中物品写成 qcItems/<id>.yml 并签名
        ↓
手工微调 qcItems/<id>.yml（type / permission / equip-lore / requirements）
        ↓
   /qc reload  →  /qc give <id>
```

### 1. 导出手持物品

```text
/qc export <id> [类型] [-f]
```

| 参数 | 说明 |
| --- | --- |
| `<id>` | 护符 ID，小写字母/数字/下划线/连字符，1-32 位；同时是文件名 |
| `[类型]` | 槽位类型。省略时按 `gui.yml` 的槽位 `type` 列表在显示名与 Lore 中逐行匹配，命中即写入 |
| `-f` / `force` | 覆盖已存在的同名文件 |

执行后插件会：

1. 剥离物品上旧的实例签名，把干净的 `Item` 写入 `qcItems/<id>.yml`，同时写好 `charm.enabled: true` 与 `charm.permission: qingcharms.charm.<id>`。
2. 重新加载护符表。
3. 把手中物品替换为带服务端签名的实例，可以直接右键装备。

导出时的两种警告：

- `type-not-in-lore`：识别到类型，但 Lore 里没有该文字。装备时仍会被 Lore 校验拦下，需要补一行包含该类型文字的 Lore，或配置 `equip-lore`。
- `no-type`：完全没识别到类型。护符会正常加载，但**只能存储、无法装备**，需要在文件里手写 `charm.type`。

### 2. `qcItems` 文件格式

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
  permission: qingcharms.charm.jade
  equip-lore: ""
  requirements:
    player-level: 0
    classes: []
```

规则：

- 根键必须是 `Item` 或 `item`，且能被 Bukkit 反序列化。
- `qcItems` 支持嵌套子目录，**文件名去掉扩展名即为护符 ID**（可以是中文，如 `伪誓之舌.yml`）。
- **`data` 以 `Item` 内的 durability 为准**。`charm.data` 不覆盖它，两者不一致时只打一条 warning；要区分玻璃/羊毛颜色等变体就写在 `Item` 里。
- 物品没有 `display-name` 时不校验名字，只按材质、`data`（若配置）和 Lore 识别。
- 同 ID 时 `config.yml` 中的定义优先。
- 文件缺 `type` 时，加载期会尝试从 Lore 推断；推断不到则保持空类型并给出 warning。

### 3. 排错：`/qc check`

手持物品执行 `/qc check`，会逐项打印插件看到的东西：

```text
&7[青玉] &f手持：GOLD_INGOT:0 名称：青玉护符
&7[青玉] 签名：&a已签名（插件副本）
&7[青玉] &a匹配护符：jade &7(类型 项链)
&7[青玉] &a装备条件全部通过（签名 / 外观 / 槽位 / Lore / 等级 / 职业 / 权限）。
```

未匹配时不只报"失败"，而是列出所有同材质候选，并逐项标记 `data`、名称、Lore 是否对上：

```text
&7[青玉] &c未匹配任何护符。同材质护符逐项对比：
&8- &fjade &7data ✔ 名称✔ Lore✘
```

### 4. 发放：`/qc give`

```text
/qc give <id> [player]
```

向目标玩家（默认自己）发放一件**带服务端签名**的护符副本。会检查目标是否有该护符权限、背包是否放得下；放不下时回滚背包，不丢物品。

---

## 护符认证与防伪造

默认 `settings.require-charm-authentication: true`。

- `/qc give` 与 `/qc export` 发出的每件护符，都写入不可见的实例 UUID 和 HMAC 签名。
- 装备、成长、构筑、API 识别**先验签**，再做材质/名字/Lore 匹配。签名令牌优先：令牌里写的是哪个护符 ID，就以哪个护符为准，不会被外观相同的其他定义抢走。
- 只复制材质、名字、Lore 的 MMOItems 物品、创造模式自定义物品不会被识别。
- `charm-auth-secret` 首次启动自动生成并持久化。**请备份，不要公开**；更换密钥会使所有旧签名失效。
- 升级存档时，未签名的旧物品默认不再识别，应通过 `/qc give` 重新发放。
- 仅在受控迁移期间可临时关闭该开关，此时会退回纯外观识别，不具备防伪造能力。

---

## 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/qc help` | `qingcharms.command` | 帮助 |
| `/qc list` | `qingcharms.command` | 列出已加载护符：ID / 材质:data / 类型 / 来源标记 |
| `/qc status` | `qingcharms.command.status` | 版本、护符数、缓存玩家数、上次保存/备份、只读状态、存储占用 |
| `/qc gui` | `qingcharms.gui` | 打开装备 GUI（也可 `/qingcharmsgui`、`/qcg`） |
| `/qc build` | `qingcharms.command` | 显示当前职业、契合特质、已激活羁绊 |
| `/qc check` | `qingcharms.command` | 诊断手持物品为什么装不上 |
| `/qc give <id> [player]` | `qingcharms.command.give` | 发放带签名的护符副本 |
| `/qc export <id> [类型] [-f]` | `qingcharms.command.admin` | 把手中物品导出为 `qcItems` 文件并签名 |
| `/qc reload` | `qingcharms.command.reload` | 重载全部配置并重新校验已装备道具 |
| `/qc save` | `qingcharms.command.save` | 强制保存在线玩家数据 |
| `/qc backup` | `qingcharms.command.backup` | 强制备份玩家数据 |

成长命令（独立入口）：

```text
/qingcharmsgrowth status [槽位|hand]
/qingcharmsgrowth level      <槽位|hand>
/qingcharmsgrowth enhance    <槽位|hand>
/qingcharmsgrowth breakthrough <槽位|hand>
/qingcharmsgrowth star       <槽位|hand>
/qingcharmsgrowth awaken     <槽位|hand>
```

别名：`/qcgrow`、`/qcprogress`。`槽位` 填 `gui.yml` 里槽位的 key（如 `necklace`），`hand` 表示手持物品。

`/qc reload` 会重新读取 `config.yml`、`gui.yml`、`classes.yml`、`progression.yml`、`language_zh.yml`，刷新已打开的 GUI，并对在线玩家的已装备护符重新校验（不再满足要求的会被卸下）。

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `qingcharms.command` | 所有人 | 使用主命令 |
| `qingcharms.command.status` | 所有人 | 查看运行状态 |
| `qingcharms.command.save` | OP | 强制保存 |
| `qingcharms.command.backup` | OP | 强制备份 |
| `qingcharms.command.reload` | OP | 重载配置 |
| `qingcharms.command.admin` | OP | 导出护符（`/qc export`） |
| `qingcharms.command.give` | OP | 发放护符 |
| `qingcharms.progression` | 所有人 | 使用成长命令 |
| `qingcharms.gui` | 所有人 | 打开装备 GUI |
| `qingcharms.charm.*` | OP | 使用全部护符 |
| `qingcharms.charm.<护符ID>` | 不配置则无需权限 | 单个护符的使用权限；未写 `permission` 的护符不校验 |

`gui.permission` 控制能否打开整个装备 GUI；单个槽位还可以在 `gui.yml` 里写 `permission` 做细粒度限制。

---

## 装备 GUI

```yaml
gui:
  row: 6                              # 或 rows，1-6
  title: "&9&l&o灵魂饰品"
  permission: qingcharms.gui
  slots:
    # key 自由命名；slot 或 position 指定格子（0 开始），也可直接用数字作 key
    helmet_13:
      slot: 13
      id: 421                         # 原版数字 ID
      id_data: 0
      name: "&9&l&o耳环槽"
      lore:
        - "&7左键卸下 | 背包右键装备 | Shift+右键替换"
      type: 耳环                      # 与护符的 type 完全一致
      action: equip
```

默认 `gui.yml` 是 6 行「灵魂饰品」布局，共 14 个装备槽：

| 槽位 key | 物品 ID | type |
| --- | --- | --- |
| `hufu-10` | 10 | 天空护符 |
| `helmet_13` | 13 | 耳环 |
| `baoshi-16` | 16 | 天空宝石 |
| `hufu-19` | 19 | 自然护符 |
| `main_hand_21` | 21 | 手环 |
| `chestplate_22` | 22 | 项链 |
| `main_hand_23` | 23 | 戒指 |
| `baoshi-25` | 25 | 自然宝石 |
| `hufu-28` | 28 | 幻象护符 |
| `leggings_31` | 31 | 腰带 |
| `baoshi-34` | 34 | 幻象宝石 |
| `hufu-37` | 37 | 神圣护符 |
| `boots_40` | 40 | 魂环 |
| `baoshi-43` | 43 | 神圣宝石 |

背景格：第 0/8 行用 `160:11`（淡蓝玻璃板），中间行剩余格用 `101`（铁栏杆）。没有关闭按钮，按 `Esc` 关闭。

### `action`

| 动作 | 行为 |
| --- | --- |
| `equip` | 左键已装备槽位卸下；背包护符右键装备，`Shift+右键` 替换 |
| `unequip` | 卸下并放回背包 |
| `refresh` / `reopen` | 刷新 GUI |
| `close` | 关闭 GUI |
| `none` | 只展示图标，不响应点击 |

`action` 为 `equip`/`unequip` 时 `type`（或 `slot-id`）必填，留空或写 `NONE` 的槽位会被**加载期直接跳过**——既不显示也不可点击。写成未知动作时槽位仍显示，但点击无效，日志会提示。

### 交互

| 操作 | 空槽 | 已占用 |
| --- | --- | --- |
| 背包护符右键 | 装备 | 提示「当前槽位已满，按 Shift+右键替换」 |
| 背包护符 `Shift+右键` | 提示「当前槽位为空，没有可替换的护符」 | 旧护符放回背包后替换；背包不足则回滚 |
| 左键装备槽 | — | 卸下并放回背包 |

所有装备入口共用同一套校验顺序：

```text
服务端签名有效 → 外观匹配（材质/data/名字/Lore）→ 槽位类型匹配
→ 装备 Lore 匹配（equip-lore 或 type 文字）→ 玩家等级 → 职业 → 护符权限
```

提示文本在 `language_zh.yml` 的 `charm.equip.*` 与 `gui.equip.*` 中修改。

### 装备反馈

仅影响表现，不参与装备状态、持久化、Lore 或属性。

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
```

`feedback.charms.<护符ID>` 可只覆盖需要改的字段。消息占位符：`{charm}`、`{name}`、`{id}`、`{type}`、`{event}`。`{charm}`/`{name}` 优先取物品实际 display-name（含觉醒改名），没有才退回护符配置的 `display-name`，最后是护符 ID。

音效走 Spigot 1.8 `Sound` API，粒子走旧版 `Effect`/`World#playEffect`，兼容 1.8-1.12.2。反馈异常被隔离，不会中断装备流程。

### 触发提示

护符效果由 ILS 触发，外部插件调用：

```java
charmAPI.notifyTrigger(player, charmId, "on-hit");
```

QingCharms 按「玩家 UUID + 护符 ID + 触发类型」做 Tick 节流，然后发一条普通聊天消息。

```yaml
trigger-message:
  enabled: true
  cooldown-ticks: 5
  format: "&7[青玉] &f{name}已触发"
```

可在 `charms.<id>.trigger-message` 中按护符覆盖。占位符：`{name}`、`{charm}`、`{id}`、`{type}`、`{trigger}`。节流只抑制提示，不影响实际效果。

---

## 职业构筑（羁绊）

构筑只做条件解析与 Lore 编译，属性仍由 ILS 读取最终 Lore 计算。

| 组件 | 职责 |
| --- | --- |
| `ClassResolver` | 只调 `player.hasPermission()` 解析职业 |
| `TagResolver` | 统计整个 GUI：每件已装备饰品给职业的每个标签各 +1 |
| `RuleResolver` | 解析 `bond` 规则并做档位筛选 |
| `BuildResolver` | 组合职业、标签、规则，处理装备校验与构筑变更 |
| `LoreCompiler` | 清扫旧动态 Lore，生成独立的构筑效果 Lore 物品 |
| `BuildSnapshot` | 固化当次结果：职业、标签计数、激活规则 |

### 标签只有一个来源

标签**只**来自 `classes.yml` 的 `classes.<id>.tags`。饰品（`config.yml` 的 `charms.*` 与 `qcItems`）不再支持 `tags`，写了会提示并忽略。

计数规则：GUI 内每件已装备饰品，都会给当前职业的**每个**标签各计 1 次。

- 圣骑士 `tags: [圣光]` 装 3 件 → `圣光 = 3`
- 圣骑士 `tags: [圣光, 守护]` 装 3 件 → `圣光 = 3`、`守护 = 3`
- 一件没装 → 全部为 0

### 职业

```yaml
classes:
  paladin:
    name: "圣骑士"                      # /qc build 显示，支持 & 颜色码
    permission: "deluxetags.tag.sqs"    # 只认权限，不认称号字符串
    tags: [圣光, 守护]
    recommended-tags: [圣光, 守护, 治疗] # 仅 /qc build 的推荐提示
  warrior:
    name: "战士"
    permission: "deluxetags.tag.warrior"
    tags: [力量, 壁垒]
```

多个职业权限可以同时生效，标签会叠加。权限由 GroupManager 之类分配。

### 规则

只有 `bond` 一种算法，**不需要写 `type`**，判定为 `tags` 中所有标签的计数**相加** `>= count`。

```yaml
rules:
  holy-bond:
    name: "圣光祝福"          # /qc build「已激活羁绊」显示的名字，不写则用规则 ID
    tags: [圣光]
    count: 2
    add-lore:
      - "&f防御: +5%"

  holy-guard-bond:
    name: "圣光守护"
    tags: [圣光, 守护]
    count: 4
    add-lore:
      - "&f伤害减免: +5%"

  paladin-build:
    name: "黎明守卫"
    classes: [paladin]        # 可选职业门槛
    tags: [守护]
    count: 2
    add-lore:
      - "&f生命: +5%"
```

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否启用，默认 true |
| `name` | 可选展示名，不写回退成规则 ID |
| `group` | 可选档位分组名，不写则按 `tags` 组合自动分组 |
| `tags` | 参与计算的标签 |
| `count` | 需要的计数总和，默认 2 |
| `classes` / `class` | 可选职业门槛 |
| `requirements` | 可选，给单个标签设最低数量 |
| `add-lore` | 必填，激活后编译进构筑 Lore 的最终属性行 |

**档位：只有最高的一档生效。** `tags` 组合相同的规则互相竞争，`count` 最高者获胜；`count` 相同的算同档并列，会一起生效。

```yaml
holy-bond:       # count: 2 → 装 2~4 件时生效
holy-bond-pro:   # count: 5 → 装 5 件后生效，低档自动熄灭
```

`count` 怎么估：

- 单标签：`count` 就等于「需要装几件」。
- 多标签：每件给每个标签各 +1，所以 `tags: [圣光, 守护] count: 4` 也是「装 2 件」。

`requirements` 给单标签设下限：

```yaml
requirements:
  圣光: 3
  守护: 1
```

`add-lore` 必须写成 ILS 能识别的最终格式（如 `&f攻击力: +10%`）。相同文本跨规则去重，并且**不会写进玩家装备**：构筑效果被编译成一件独立的虚拟物品，只在 `EquipmentSnapshot` 中交给 ILS。

### 已移除的旧写法

`type`、`synergy`、`conflict`、`mode`、`suppress-rules` 都已下线：写了会在控制台提示但规则仍按 `bond` 生效；冲突机制整体移除，装备不再互相排斥。

### 玩家侧

```yaml
requirements:
  player-level: 30      # 只表示原版玩家等级，不是护符成长等级
  classes: [paladin]    # 满足任意一个即可
```

`/qc build` 输出当前职业名、契合特质和已激活羁绊，仅展示，不会自动装备。

---

## 护符成长系统

成长配置独立在 `progression.yml`，只管理成长状态与 Lore。

| 组件 | 职责 |
| --- | --- |
| `ProgressionConfig` | 读取激活 Lore、等级、强化、突破、星级、觉醒、资源与反馈 |
| `ProgressionRecord` | 按道具唯一 ID 保存等级、强化、突破、星级、觉醒、击杀数、佩戴时长 |
| `ResourceService` | 校验并扣除佩戴时长、击杀数、原版经验、Vault 金币、PlayerPoints |
| `ProgressionLoreCompiler` | 删除激活 Lore、清理旧成长 Lore、在原位置生成最终 Lore |
| `ProgressionService` | 执行手动升级、强化、突破、升星、觉醒 |
| `ProgressionListener` | 记录怪物击杀与在线佩戴时间 |

### 启用条件

只有 Lore 命中 `progression.activation-lore` 的道具才进入成长系统：

```yaml
progression:
  enabled: true
  activation-lore:
    - "可成长"
```

- 支持多行检测文字，按去色后的文本做包含匹配。
- 装备时命中的那行 Lore 会被删除，并在**原位置**替换为成长 Lore。
- 未命中且没有既有成长状态的道具完全不参与成长，不累计佩戴时间或击杀数。
- 已启用成长的道具在刷新 Lore 时保持状态，不依赖激活 Lore 再次出现。

### 成长 Lore

```yaml
progression:
  lore:
    level: "&7等级: {level}/{max}"
    enhance: "&7强化: +{enhance}"
    breakthrough: "&7突破: {stage}阶段"
    star: "&7星级: {star}"
    awakened: "&7觉醒: 已觉醒"
```

`unlock-lore` 由编译器生成。除被命中的激活 Lore 外，编译器不替换原始 ILS Lore；成长、构筑、静态 Lore 的相同文本会去重，避免 ILS 重复计算。

### 五个成长维度

| 维度 | 关键字段 |
| --- | --- |
| `level` | `max-level`、`cost`、`costs.<等级>`、`unlock-lore.<等级>` |
| `enhance` | `max`、`success-rate`、`cost`、`costs.<目标等级>`、`unlock-lore` |
| `breakthrough` | `stages[].name/level-cap/enhance-cap/cost/unlock-lore/feedback` |
| `star` | `max`、`formats`、`stages[].format/required-breakthrough/level-cap-bonus/enhance-cap-bonus/cost` |
| `awakening` | `one-time`、`requirements`、`cost`、`display-name`、`unlock-lore` |

- 全部由玩家手动触发，不会自动升级或自动强化。
- 突破要求当前强化达到当前上限；升星可要求指定突破阶段；觉醒是一次性操作。
- 默认 `star` 与 `awakening` 为 `enabled: false`。

### 资源

| 字段 | 含义 |
| --- | --- |
| `wear-seconds` | 该物品累计装备时间（秒） |
| `kills` | 装备期间累计击杀怪物数 |
| `experience` | 原版玩家经验 |
| `vault` | Vault 金币 |
| `playerpoints` | PlayerPoints 点数 |

前两项是每件道具自己的累计资源；后三项在操作成功时从玩家账户扣除。多种资源可组合，任意一项不足则整次操作被拒绝。

### 数据绑定

每件成长道具首次绑定时生成唯一 ID，写入物品的**隐藏 Lore 标记**与 `playerdata/<UUID>.yml` 的 `item-ids`、`progression` 节点。

- 标记不显示文字；为让提示框不多出空行，它追加在**最后一行可见 Lore 的末尾**，物品完全没有 Lore 时才单独占一行。
- 旧 Lore 里的 `Unique ID` / `UUID` / `Item ID` 会被迁移到隐藏标记并清理。
- 进度按实例唯一 ID 保存，不按护符 ID 或外观共享。

---

## 持久化

```text
playerdata/<UUID>.yml
```

```yaml
players:
  00000000-0000-0000-0000-000000000000:
    charms:
      jade: true                    # 已获得的护符
    equipped:
      chestplate_22: <完整 ItemStack>
    item-ids:
      "equipped:chestplate_22":
        unique-id: 550e8400-e29b-41d4-a716-446655440000
        fingerprint: 3204dbca-2161-3bb5-88c0-53271493d87c
    progression:
      550e8400-e29b-41d4-a716-446655440000:
        unique-id: 550e8400-e29b-41d4-a716-446655440000
        level: 3
```

- `equipped` 保存完整 `ItemStack`，含名称、Lore、附魔和其他 ItemMeta。
- 实例 UUID 存在 `item-ids` 与 `progression` 中，不写进玩家可见 Lore。
- 装备变化会更新数据版本，ILS 可据此刷新读取结果。
- 写入采用临时文件 + 同步 + 原子替换；`settings.player-save-interval-seconds` 控制异步保存频率（默认 30 秒）。
- 定时备份到 `DataBacked/`，由 `player-backup-interval-minutes`（60）、`player-backup-retention`（7 份）、`player-backup-max-age-hours`（168 小时）控制清理。
- `/qc save`、`/qc backup` 可手动强制触发。

---

## ILS 桥接

```java
EquipmentSnapshot snapshot = api.getEquipmentSnapshot(player);
List<ItemStack> completeItems = snapshot.getItems();
```

快照内容 = 全部已装备护符的完整 `ItemStack` + 至多一件**虚拟构筑 Lore 物品**（构筑效果的去重结果，不会出现在玩家装备里）。

兼容入口：

```java
List<ItemStack> completeItems =
        ((QingCharmsPlugin) plugin).getEquippedCharmItems(player);
```

`getEquippedCharmItems` 只返回玩家实际装备，不含构筑虚拟物品。

ILS 的 `GearStats` 读取这些 `ItemStack` 的完整 Lore 自行计算，QingCharms 不读取、拆分或汇总属性值。

---

## API

API 合约版本：`1.0.0`。方法自 `0.0.1` 起陆续提供，旧入口保留以兼容。

### 查询接口（`CharmQueryAPI`）

```java
CharmQueryAPI query = ((QingCharmsPlugin) plugin).getAPI();

Optional<CharmDefinition> charm = query.getCharm(charmId);
boolean charmItem  = query.isCharm(item);
String uniqueId    = query.getUniqueId(item);
ItemStack equipped = query.getEquippedItem(player, slotId);
List<ItemStack> equippedCharms = query.getEquippedCharms(player);
Set<String> owned  = query.getOwnedCharmIds(player);
```

只读，返回副本或不可变集合，不改道具、Lore 或玩家数据。

### 操作接口（`CharmOperationAPI`）

```java
CharmOperationAPI operations = ((QingCharmsPlugin) plugin).getAPI();

boolean equipped  = operations.equip(player, item, slotId);
boolean removed   = operations.unequip(player, slotId);
boolean notified  = operations.notifyTrigger(player, charmId, triggerType);
```

只允许在 Bukkit 主线程调用。`notifyTrigger()` 返回 `true` 仅表示提示已发出，不计算属性、不触发战斗效果。

### 兼容方法（`CharmAPI`）

```java
Collection<CharmDefinition> charms = api.getCharms();
Optional<CharmDefinition> byItem   = api.getCharmByItem(item);
BuildResolution build              = api.resolveBuild(player);
Set<String> activeClasses          = api.getActiveClasses(player);
Set<String> recommendedTags        = api.getRecommendedTags(player);
```

`getCharmByMaterial(Material)` 已废弃（材质无法唯一确定护符），请用 `getCharmByMaterial(Material, ItemStack)`。

### Bukkit 事件

| 分类 | 事件 |
| --- | --- |
| 装备 | `CharmEquipEvent`、`CharmUnequipEvent`、`CharmReplaceEvent` |
| 构筑 | `CharmBuildChangeEvent`、`CharmRuleActivateEvent`、`CharmRuleDeactivateEvent` |
| 成长 | `CharmLevelEvent`、`CharmEnhanceEvent`、`CharmBreakthroughEvent`、`CharmStarEvent`、`CharmAwakeningEvent`、`CharmProgressionChangeEvent` |
| 提示 | `CharmTriggerNotifyEvent` |

均使用标准 `HandlerList`，在主线程触发。装备、强化、突破、升星、觉醒在数据和资源真正变化前触发，可以取消。

不提供 `CharmDropEvent`、`CharmLoadoutSwitchEvent`、`CharmEffectCalculateEvent`——QingCharms 不实现掉落、预设切换或属性计算。

API 修改只增加默认方法或重载，不删除既有方法和事件构造器。

---

## `config.yml` 参考

### `settings`

```yaml
settings:
  require-charm-authentication: true
  charm-auth-secret: ""            # 首次启动自动生成，勿公开
  player-save-interval-seconds: 30
  player-backup-interval-minutes: 60
  player-backup-retention: 7
  player-backup-max-age-hours: 168
  player-backup-folder: DataBacked
  qc-items-folder: qcItems
```

### `charms`

```yaml
charms:
  example:
    enabled: false
    material: 95          # 数字 ID 或材质名
    data: 14
    display-name: "&a红色玻璃护符"
    lore:
      - "&7项链护符"
      - "&c攻击: &r+1000000"
    type: 项链
    equip-lore: ""
    permission: qingcharms.charm.example
    trigger-message:
      cooldown-ticks: 10
      format: "&7[青玉护符] &f效果已触发"
    requirements:
      player-level: 30
      classes: [paladin]

  false-oath-tongue:
    enabled: false
    item-file: "qcItems/伪誓之舌.yml"   # 必须在本插件数据文件夹内
    permission: qingcharms.charm.false-oath-tongue
    requirements:
      player-level: 0
      classes: []
```

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否加载 |
| `material` / `data` | 材质与 Data/Durability |
| `item-file` | 直接引用 `qcItems` 中的物品文件 |
| `display-name` | 匹配用显示名 |
| `lore` | 匹配用 Lore；属性由 ILS 从 Lore 读取 |
| `type` | 槽位类型，必须与 GUI 槽位一致 |
| `equip-lore` | 装备时 Lore 中必须包含的行 |
| `permission` | 使用权限 |
| `requirements` | `player-level` 玩家等级、`classes` 职业 ID 列表 |
| `attributes` | 遗留展示节点，不参与任何计算 |
| `trigger-message` | 按护符覆盖触发提示 |

### `language_zh.yml`

所有消息都在语言文件里，支持 `&` 颜色码与 `{0}`、`{1}` 占位符。改完执行 `/qc reload`。

> 注意：服务器上已存在的 `language_zh.yml` **不会**被新版本 JAR 覆盖。升级插件后新增/删除的消息键不会自动同步，需要手动比对。

---

## 常见问题

### 护符装不上

按顺序查：

1. `/qc check` 是否显示「已签名」——未签名说明不是插件发出的副本。
2. 护符 `type` 是否与目标槽位 `type` 完全一致。
3. 物品 Lore 是否包含该 `type` 文字或配置的 `equip-lore`。
4. `requirements.player-level` / `classes` 是否满足。
5. 玩家是否有 `qingcharms.charm.<id>` 权限。
6. 操作方式是否为背包右键 / `Shift+右键`。

### 改了 Lore 但属性没变

1. 改的是物品实际的 Lore，不是 `config.yml` 里的匹配用 `lore`。
2. 已装备的旧副本需要卸下重装，或执行 `/qc reload` 触发重新校验与 Lore 刷新。
3. ILS 已安装并读取到 QingCharms 的桥接。
4. ILS 的属性名与数值格式配置正确。

### `attributes` 里的数值为什么不生效

预期行为。`attributes` 不属于战斗系统，属性必须写进物品 Lore 由 ILS 计算。

### `/qc reload` 后玩家护符被卸下了

正常保护逻辑。`revalidation-removed` 表示重载后该护符不再满足新配置的槽位、Lore、等级或职业条件，插件会主动卸下并放回背包。

---

## 版本差异

### 0.2.0

- **移除**：护符背包（`/qc bag`、`/qc open`、`qingcharmsbag`、`charm-bag` 配置段、`bag/` 整个包）。
- **移除**：护符展示字段 `rarity`、`level`、`category`、`source-description`。
- **移除**：指令 `/qc add`、`/qc remove`、`/qc set`、`/qc on|off` 及全部别名。护符改为「`/qc export` → 手改 YAML → `/qc reload`」流程。
- **重构**：构筑标签只来自 `classes.yml`，计数改为「每件饰品给每个职业标签 +1」；规则只保留 `bond`，新增 `name`、`group` 与档位机制；冲突机制下线。
- **拆分**：`classes`、`rules` 从 `config.yml` 迁到独立的 `classes.yml`。
- **默认 GUI**：换成 6 行「灵魂饰品」14 槽布局。
- 版本号 `0.1.0` → `0.2.0`。

### 0.1.0

- `/qc export` 自动识别槽位类型并就地签名。
- 新增 `/qc check` 诊断。
- 修复无 `display-name` 护符永远匹配不上自己、`qc give` 提示框多出空行、`qcItems` 有 `data` 时装不上等问题。
- 装备/卸下提示改为显示物品 display-name。
