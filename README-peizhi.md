# QingCharms 配置教学（新手篇）

> 适用版本：QingCharms 0.1.x　|　服务端：Spigot / Bukkit 1.8 – 1.12.2
> 读完这份文档，你能独立完成：**做出护符 → 配好槽位 → 发给玩家 → 设计一套职业构筑（职业标签 + 羁绊）**。

---

## 目录

1. [先看这 6 行](#一先看这-6-行)
2. [插件到底管什么](#二插件到底管什么)
3. [文件地图](#三文件地图)
4. [10 分钟做出第一条护符](#四10-分钟做出第一条护符)
5. [护符字段速查 + 4 个必踩的坑](#五护符字段速查--4-个必踩的坑)
6. [装备 GUI 配置](#六装备-gui-配置)
7. [构筑系统：职业标签 + 羁绊规则](#七构筑系统职业标签--羁绊规则)
8. [一套可直接复制的完整构筑示例](#八一套可直接复制的完整构筑示例)
9. [怎样才算"合理"的构筑](#九怎样才算合理的构筑)
10. [成长系统最小配置](#十成长系统最小配置可选)
11. [命令与权限表](#十一命令与权限表)
12. [排错对照表](#十二排错对照表)
13. [上线前检查清单](#十三上线前检查清单)

---

## 一、先看这 6 行

1. QingCharms **不算属性**。它只管"能不能装上、装到哪个槽、哪些构筑规则生效"。
2. 属性、暴击、吸血、技能……全部写在**物品 Lore** 里，由 **ItemLoreStats（ILS）** 去读。
3. 所以护符的 Lore 必须写成 **ILS 认识的格式**，QingCharms 只是搬运工。
4. 有 4 个文件要改：`config.yml`（护符 + 反馈 + 全局设置）、`classes.yml`（职业 + 构筑规则）、`gui.yml`（槽位 + 背包）、`progression.yml`（成长，可选）。
5. 改完执行 `/qc reload`，**不要重启服务器**。
6. 护符必须用 `/qc give` 发放，手搓的、别的插件复制的同款物品**不会被识别**（防伪签名机制）。

---

## 二、插件到底管什么

| 事情 | 谁负责 |
|---|---|
| 护符身份识别、防伪 | QingCharms |
| 装备槽位、替换、卸下 | QingCharms |
| 构筑条件（职业标签 / 羁绊规则） | QingCharms |
| 把"构筑效果 Lore"打包给 ILS | QingCharms |
| 攻击力、防御、暴击、吸血等数值 | **ILS** |
| 被动触发、技能等级、战斗结算 | **ILS** |

记住一句话：**QingCharms 决定"谁能装什么"，ILS 决定"装了有多强"。**

所以你写 `add-lore: "&f攻击力: +10%"` 时，QingCharms 不会去算这 10%，它只是把这一行文本塞给 ILS，让 ILS 自己解析。**格式写错，效果就是 0，插件不会报错。**

---

## 三、文件地图

插件首次启动后，`plugins/QingCharms/` 下会生成：

```
plugins/QingCharms/
├── config.yml          ← 护符定义、反馈、全局设置
├── classes.yml         ← 职业（classes）与构筑规则（rules）
├── gui.yml             ← 装备 GUI 槽位、护符背包
├── language_zh.yml     ← 所有聊天栏提示文字
├── progression.yml     ← 成长系统（等级/强化/突破/星级/觉醒）
├── qcItems/            ← 一件护符一个 YAML（复杂物品推荐放这里）
│   └── 项链/xxx.yml
├── playerdata/         ← 玩家数据，别手改
├── DataBacked/         ← 自动备份
└── charm-bags/         ← 护符背包数据
```

**改哪个文件？**

| 我想… | 改这里 |
|---|---|
| 加 / 改护符 | `config.yml` 的 `charms:`，或往 `qcItems/` 放文件 |
| 改槽位位置、图标、行数 | `gui.yml` 的 `gui:` |
| 改背包容量、每页数量 | `gui.yml` 的 `charm-bag:` |
| 做职业、羁绊、套装 | `classes.yml` 的 `classes:` 和 `rules:` |
| 做可升级护符 | `progression.yml` |
| 改提示语 | `language_zh.yml` |

> 护符数量多（>20 件）时，建议**全部放 `qcItems/`**，`config.yml` 只留全局设置，职业和规则放 `classes.yml`，方便维护。

---

## 四、10 分钟做出第一条护符

我们做一个最经典的：**青玉项链**，装到"项链"槽。

### 第 1 步：在 `gui.yml` 里确定槽位

打开 `gui.yml`，找到 `gui:` 下的 `slots:`。默认已经有 6 个槽：

```yaml
gui:
  row: 3                       # 3 行 = 27 格（每行 9 格，编号 0 开始）
  permission: qingcharms.gui
  title: "&8青玉护符装备"
  slots:
    necklace:
      slot: 4                  # 第 1 行第 5 格
      id: 98                   # 图标物品数字 ID（98 = 苔石砖）
      id_data: 0
      name: "&a项链饰品槽"
      lore:
        - "&7左键卸下 | 背包右键装备 | Shift+右键替换"
      type: 项链                # ★ 关键：槽位类型
      action: equip
```

`type: 项链` 就是**槽位类型**。它可以是任意中文字符串（`项链`、`手饰`、`腰带`、`天空`……），插件不限制词表，只要求**和护符的 `type` 字面一致**。

### 第 2 步：在 `config.yml` 里定义护符

找到 `charms:`，把自带的 `example:` 当模板改：

```yaml
charms:
  jade-necklace:
    enabled: true                        # ★ 必须是 true，默认是 false
    material: 95                         # 材质：可以写数字 ID 或 Bukkit 名
    data: 14                             # Data 值（1.8 的颜色变体），14 = 红色
    display-name: "&a青玉项链"
    lore:
      - "&7项链护符"
      - "&8[护符槽位: 项链]"              # 这一行会被当成"可装备凭证"
      - ""
      - "&f攻击力: &c+10%"
      - "&f生命值: &c+30"
    type: 项链                            # ★ 必须和槽位 type 一模一样
    equip-lore: "&8[护符槽位: 项链]"      # ★ 写这个更稳，见下一节
    rarity: RARE
    category: 项链
    level: 1
    permission: qingcharms.charm.jade-necklace
    # 注意：饰品不写 tags，构筑标签全部来自 classes.<id>.tags
```

### 第 3 步：重载 + 发放 + 装备

```
/qc reload
/qc list                                    ← 应该能看到 jade-necklace，状态"启用"
/qc give jade-necklace                      ← 发给自己（也可以 /qc give jade-necklace 玩家名）
/qc gui                                     ← 打开装备界面
```

然后：**保持 GUI 开着，右键背包里的青玉项链** → 装备成功，槽位里出现护符图标。

卸下：**左键点槽位**。
替换：**Shift + 右键**背包里另一件项链。

### 它为什么能装上？——判定链

装备时会按顺序过 5 道关，任何一道不过都会失败：

```
① 服务端签名有效（是 /qc give 发出来的）
② 物品外观匹配（材质 + data + 显示名 + Lore 全部对上）
③ 槽位类型匹配（护符 type == 槽位 type）
④ Lore 凭证匹配（equip-lore 那行在物品上）
⑤ 等级 / 职业 / 权限满足
```

**找问题时就按这 5 步倒查**，比瞎猜快 10 倍。

排查工具：手持那件装不上的物品执行 `/qc check`，它会直接告诉你签名状态、匹配到的护符 ID，以及与同材质护符逐项对比（data / 名称 / Lore 哪个✘）。

> ⚠️ **`qcItems` 文件里的 `data` 规则**：`Item` 块里的 `damage`/`data` 就是这件物品的变体（玻璃、羊毛颜色等），**以它为准**；`charm:` 下再写 `data` 不会生效，只会在控制台打一条 warning。要区分变体请写在 `Item` 里。

---

## 五、护符字段速查 + 4 个必踩的坑

### 字段总表

| 字段 | 必填 | 说明 |
|---|---|---|
| `enabled` | ✅ | `config.yml` 里**默认 false**，必须写 `true` |
| `material` | ✅ | 数字 ID（`95`）或名字（`GOLD_INGOT`），也支持 `95:14` 写法 |
| `data` | ❌ | 不写 = **不校验** Data（任何颜色变体都能识别）；写了就必须完全一致 |
| `display-name` | ❌ | 参与识别，必须和实际物品显示名一致（`§` 和 `&` 视为同一个符号，但颜色代码本身要写对） |
| `lore` | ❌ | 参与识别，**配置的每一行都必须出现在物品上** |
| `type` | ✅ | 槽位类型；`NONE` 表示不需要槽位 |
| `equip-lore` | ❌ | 额外凭证行；不写则要求 Lore 里含 `type` 文字 |
| `rarity` | ❌ | `COMMON` / `RARE` / `EPIC` / `LEGENDARY`，写错默认 COMMON |
| `category` | ❌ | 背包分类，默认等于 `type` |
| `level` | ❌ | 展示等级（背包排序用），**不是**装备等级要求 |
| `source-description` | ❌ | 只做展示，不实现掉落/合成 |
| `permission` | ❌ | 默认 `qingcharms.charm.<id>` |
| ~~`tags`~~ | — | **已移除**：构筑标签只来自 `classes.<id>.tags`，写了会被忽略 |
| `requirements` | ❌ | 装备门槛：`player-level` / `classes` |
| `trigger-message` | ❌ | 覆盖全局触发提示 |

### 坑 1：`charms` 下的 `enabled` 默认是 false

```java
// CharmManager 里，config.yml 的护符：
if (charm == null || !charm.getBoolean("enabled", false)) continue;
```

但 `qcItems/` 里的文件默认是 **true**：

```java
if (!metadata.getBoolean("enabled", true)) continue;
```

**结论**：`config.yml` 里不写 `enabled: true` 就不会加载；`qcItems` 里不写反而会加载。史上最容易"配了没反应"的原因就是这一条。

### 坑 2：`lore` 是"子集匹配"，不是"覆盖"

插件不会用你配置的 Lore 去覆盖物品，它是**拿你的 Lore 去核对物品**：

- 物品的显示名必须和你写的 `display-name` **完全相同**（`§` 和 `&` 会被当成同一个符号，但颜色码要写对：`§a青玉项链` 和 `&a青玉项链` 等价，写成 `青玉项链` 就对不上）。
- 你写的**每一行** Lore 都必须在物品上找到（同样只忽略 `§`/`&` 的写法差异，文本和颜色码必须一致）。

所以：物品上多几行（ILS 属性）没问题；**你配置里多写一行、物品上没有，就识别失败**。

最稳的写法是只写"稳定不变的锚点"：

```yaml
display-name: "&a青玉项链"
lore:
  - "&7项链护符"
  - "&8[护符槽位: 项链]"
```

不要把你后期会调整的属性行写进 `lore`（除非你同步改了物品）。

### 坑 3：`type` 与 `equip-lore` 的关系

| 情况 | 装备判定 |
|---|---|
| 配了 `equip-lore` | 物品 Lore 里必须**整行等于** `equip-lore` |
| 没配 `equip-lore` | 物品 Lore 里必须**包含 `type` 这两个字** |

举例：`type: 项链`
- Lore 有 `&7项链护符` → 包含"项链" → 通过
- Lore 只有 `&7饰品` → 不通过 ❌

所以**推荐统一用 `equip-lore` 打标记行**：`"&8[护符槽位: 项链]"`，既有辨识度又不会被别的文本误伤。

### 坑 4：`/qc add` 帮你写对了，手改反而写错

用 `/qc add jade-necklace 95:14 项链` 时，插件自动生成：

```yaml
lore:
  - "&7项链护符"
  - "&8[护符槽位: 项链]"
equip-lore: "&8[护符槽位: 项链]"
```

**这是它自己的标准模板**，照抄不会错。手写配置时建议直接沿用这个格式。

> ⚠️ 护符 ID 只能用**小写字母、数字、下划线、连字符**，长度 1~32，**不能用中文**。
> 中文请写在 `display-name` 里（`&a青玉项链`），ID 用 `jade-necklace`。

### 关于复杂物品（带附魔、NBT、多行 Lore）

当护符是从 ILS 或其他插件导出的复杂物品时，用 `qcItems/` 更省事：

```bash
# 手里拿着物品，执行：
/qc export jade-necklace
```

会在 `qcItems/jade-necklace.yml` 生成完整的 `Item:` 序列化块；`type` 会自动识别，剩下的按需补：

```yaml
Item:
  ==: org.bukkit.inventory.ItemStack
  type: GOLD_INGOT
  amount: 1
  meta:
    ==: ItemMeta
    meta-type: UNSPECIFIC
    display-name: "§a青玉项链"
    lore:
      - "§7项链护符"
      - "§8[护符槽位: 项链]"

charm:
  enabled: true
  type: 项链          # 导出时自动写入；也可以 /qc export jade-necklace 项链 手动指定
  rarity: LEGENDARY
  permission: qingcharms.charm.jade-necklace
```

`/qc export` 做了三件事：

1. 去掉物品上的旧签名，写入干净的模板；
2. 按 `gui.yml` 里装备槽位的 type 列表，在物品**显示名和 Lore** 里找第一个命中的类型写进 `type`；
3. 把手里的物品**换成带服务端签名的副本**，可以直接右键装备 —— 所以 `/lore` 做的物品不需要再 `/qc give` 一次。

改完 Lore 想重新导出：`/qc export jade-necklace -f`。

> 关键前提：**Lore 里必须出现类型文字**（如「项链」），或用 `equip-lore` 指向某一行。
> 自动识别只做「Lore 里有没有这个词」的判断，识别不到就会提示 `no-type`，那件护符能存进背包但装不上。
> 同 ID 时 `config.yml` 优先；`qcItems` 支持子目录，文件名（去扩展名）就是护符 ID。
> 注意：`/qc export` 生成的目录是平的，想分类就手动挪进子文件夹。

---

## 六、装备 GUI 配置

### 布局怎么算

`row: 3` → 27 格，编号 `0 ~ 26`，从左到右、从上到下：

```
 0  1  2  3  4  5  6  7  8
 9 10 11 12 13 14 15 16 17
18 19 20 21 22 23 24 25 26
```

推荐 6 槽布局（默认配置就是这样）：

```yaml
gui:
  row: 3
  title: "&8青玉护符装备"
  permission: qingcharms.gui
  slots:
    helmet:      { slot: 0, id: 98, name: "&a头盔饰品槽", type: 头盔, action: equip }
    chestplate:  { slot: 1, id: 98, name: "&a胸甲饰品槽", type: 胸甲, action: equip }
    leggings:    { slot: 2, id: 98, name: "&a护腿饰品槽", type: 护腿, action: equip }
    boots:       { slot: 3, id: 98, name: "&a靴子饰品槽", type: 靴子, action: equip }
    necklace:    { slot: 4, id: 98, name: "&a项链饰品槽", type: 项链, action: equip }
    sky:         { slot: 5, id: 98, name: "&a天空饰品槽", type: 天空, action: equip }
    close:       { slot: 8, id: 35, id_data: 14, name: "&c关闭 GUI", action: close }
```

> YAML 支持行内 `{ }` 写法，但**花括号里不能换行**；不熟悉就老老实实缩进写。

### 槽位字段

| 字段 | 说明 |
|---|---|
| `slot` / `position` | 格子编号；也可以用数字当 key：`slots: { 4: {...} }` |
| `id` / `id_data` | 图标物品（原版数字 ID + Data） |
| `name` / `lore` | 图标的名字和说明 |
| `type` / `slot-id` | **槽位类型**，与护符 `type` 对应。`action` 为 `equip`/`unequip` 时**必填**，留空或写 `NONE` 该槽位会被直接跳过（不显示、不可点击） |
| `action` | `equip` / `unequip` / `refresh` / `reopen` / `close` / `none` |
| `permission` | 可选，配了就要求玩家有这个权限才能用该槽 |

### 常见错误

- `slot` 超出 `rows*9` → 该槽位**直接被忽略**，日志里会有 warning。
- 两个槽位用同一个 `slot` 编号 → 后一个被忽略。
- `row` 写成 `row: 0` 或 `row: 9` → 自动夹到 1~6。
- **`action: equip` 但没写 `type`** → 槽位被跳过，玩家看不到这个格子（日志会提示 `requires 'type'`）。
- 写成 `action: interface` 之类的旧动作 → 格子还在但点了没反应，日志会提示 `unsupported action`。

### 护符背包（可选）

```yaml
charm-bag:
  enabled: true
  title: "&8青玉护符背包"
  rows: 6
  page-size: 45          # 每页格数，不能超过 (rows-1)*9
  capacity: 180          # 每人总容量
  permission: qingcharms.bag
  sort:
    default: rarity      # NAME / LEVEL / RARITY / TYPE
```

---

## 七、构筑系统：职业标签 + 羁绊规则

这是 QingCharms 最有价值、也最容易配崩的部分。**现在只有两个概念，比以前简单一半。**

### 7.1 两个概念

| 概念 | 写在哪 | 作用 |
|---|---|---|
| **职业 classes** | `classes.yml` 的 `classes:` | 定义职业，以及该职业的 `tags`（构筑标签的唯一来源） |
| **规则 rules** | `classes.yml` 的 `rules:` | 数标签 → 够了就给额外 Lore（只有 `bond` 一种） |

> ★ **饰品不再写 `tags`**。一件饰品能贡献什么标签，完全由「装备它的玩家是哪个职业」决定。
> 老配置里饰品残留的 `tags:` 不会报错，只会被忽略并在控制台提示。

数据流：

```
职业 tags（classes.<id>.tags）─┐
                              ├─→ TagResolver：遍历 GUI 内每件已装备饰品，
GUI 内已装备的件数 ────────────┘   每件给职业的每个标签各 +1
        │
        ↓
RuleResolver 判定（sum(tags) >= count）─→ LoreCompiler 生成效果 Lore
        │                                        │
        ↓                                        ↓
   BuildSnapshot                       虚拟 PAPER 物品 → 交给 ILS 计算
```

### 7.2 职业怎么定义

只认权限节点，没有第二种判断方式：

```yaml
classes:
  paladin:
    name: "圣骑士"                       # 展示名
    permission: "deluxetags.tag.sqs"     # ★ 有这个权限 = 这个职业
    tags: [圣光, 守护]                   # ★ 构筑标签的唯一来源
    recommended-tags: [圣光, 守护, 治疗]  # 只用于 /qc build 的推荐提示，不参与判定
    titles: ["黎明守卫"]                 # 可选，纯展示称号
  warrior:
    name: "战士"
    permission: "deluxetags.tag.warrior"
    tags: [力量, 壁垒]
    recommended-tags: [力量, 壁垒, 物理]
```

- 权限由 GroupManager / LuckPerms / DeluxeTags 分配，插件只调用 `hasPermission()`。
- **不认称号名字**，也不认 PlaceholderAPI 变量 —— 想让某个组算圣骑士，就给他 `deluxetags.tag.sqs`。
- 玩家**可以同时拥有多个职业**，标签会叠加。

### 7.3 计数规则：`count` 到底在数什么

一句话：**GUI 里装了几件饰品，职业的每个标签就有几点。**

| 玩家 | 职业 tags | 已装备 | 圣光 | 守护 |
|---|---|---|---|---|
| 圣骑士 | `[圣光, 守护]` | 0 件 | 0 | 0 |
| 圣骑士 | `[圣光, 守护]` | 1 件 | 1 | 1 |
| 圣骑士 | `[圣光, 守护]` | 3 件 | 3 | 3 |
| 战士 | `[力量, 壁垒]` | 3 件 | 0 | 0 |

三条要点：

1. **没装东西 = 全 0**，构筑不成立（不会出现"空手吃满羁绊"）。
2. **只数已装备的饰品**（GUI 里有物品的槽位），背包里放着不算。
3. 多个职业同时拥有时，每个职业的标签分别累加。

所以：

- 单标签时，`count` 就等于**需要装备几件**。`tags: [圣光] count: 2` = 装 2 件。
- 多标签时，每件饰品会给每个标签各 +1，`tags: [圣光, 守护] count: 4` 也是**装 2 件**（2×2=4）。

### 7.4 规则：只有 bond，不写 `type`

```yaml
rules:
  # ① 装 2 件就吃到的入门羁绊
  dawn-bond-2:
    enabled: true
    name: "黎明初鸣"                 # ★ /qc build 第 4 行「已激活羁绊」显示这个名字
    tags: [圣光]
    count: 2
    add-lore:
      - "&f防御: +5%"

  # ② 装 4 件的进阶羁绊
  dawn-bond-4:
    enabled: true
    name: "黎明共鸣"
    tags: [圣光]
    count: 4
    add-lore:
      - "&f攻击力: +8%"

  # ③ 职业门槛（classes 可选，不写则不限职业）
  paladin-oath:
    enabled: true
    name: "黎明守卫"
    classes: [paladin]
    tags: [守护]
    count: 3
    add-lore:
      - "&f生命: +5%"
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | ❌ | 展示名。`/qc build` 的「已激活羁绊」显示它；不写就回退成规则 ID |
| `group` | ❌ | 档位分组名；不写则按 `tags` 组合自动分组 |
| `tags` | ✅ | 参与计算的标签，取职业 `tags` 里的名字 |
| `count` | ❌ | 需要的计数总和，不写默认 2 |
| `classes` / `class` | ❌ | 限定只有这些职业能触发；不写则不限 |
| `requirements` | ❌ | 给单个标签设最低数量，见下 |
| `add-lore` | ✅ | 激活后编译进构筑 Lore 的最终属性行 |

#### 档位：只亮最高的一档

`tags` 组合相同的规则是**同一档位的竞争者**，只有 `count` 最高的那条生效：

```yaml
rules:
  holy-bond:            # tags: [圣光] count: 2
  holy-bond-pro:        # tags: [圣光] count: 5
```

| 装了圣光件数 | 生效的规则 |
|---|---|
| 0~1 | 无 |
| 2~4 | `holy-bond`（圣光祝福） |
| 5+ | `holy-bond-pro`（超级圣光祝福），**低档不再叠加** |

- `count` 相同的规则算同档并列，会同时生效（例如同为 2 件的「防御 +5%」和「攻击 +8%」）。
- `tags` 组合不同（`[圣光]` vs `[圣光, 守护]`）就是不同档位，彼此不竞争，可以同时亮。
- 想让写法不同但逻辑同档的规则归一档，给它们写同一个 `group: "圣光"`。

> ⚠️ 低档和高档别写**完全一样**的 `add-lore` 文本，否则就算只亮高档，去重后也看不出变化。

`requirements` 用来卡单个标签（多标签规则才用得上）：

```yaml
  holy-mixed:
    tags: [圣光, 守护]
    count: 3
    requirements:
      圣光: 2          # 圣光至少 2 点
    add-lore:
      - "&f防御: +8%"
```

> ⚠️ **坑：`rules` 下的 `enabled` 默认是 `true`。**
> `classes.yml` 自带的示例规则都写了 `enabled: false`。你复制模板时**如果把这行删了，规则反而会启用**。

> ⚠️ **已移除的旧写法**：`type`（bond/synergy/class/conflict）、`mode`、`suppress-rules`、`message`，
> 以及饰品上的 `tags`。规则里写了 `type` 只会打一条 warning，规则仍按 bond 生效；
> 冲突（互斥）机制整体下线，装备不再互相排斥，`Shift + 右键` 依然是「替换已占用槽位」。

### 7.5 构筑效果 Lore 去哪了？

**它不会写到你的饰品上。** 插件会创建一个虚拟 `PAPER` 物品，把这些 Lore 挂上去，然后随 `EquipmentSnapshot` 一起交给 ILS。

好处：饰品本身的 Lore 不会被反复改写、不会互相污染。
代价：**`add-lore` 必须是 ILS 能认的最终格式**，写错就是 0 效果，而且没有报错。

几条硬规则：

- 每条规则**只生成一次**；不同规则写了**同样的文本会被去重**，只生效一次。
- 文本如果和饰品自带的 Lore 完全一样，也会被去重（不会叠加）。
- 所以同一条属性不要在多条规则里重复写，否则你以为是"叠加 3 层"，实际只算 1 层。

---

## 八、一套可直接复制的完整构筑示例

设计目标：**"黎明守卫"（圣骑士向）**——装备件数决定羁绊档位，同时演示职业门槛。

### 8.1 职业

```yaml
classes:
  paladin:
    name: "圣骑士"
    permission: "deluxetags.tag.sqs"
    tags: [圣光, 守护]
    recommended-tags: [圣光, 守护, 治疗]
  warrior:
    name: "战士"
    permission: "deluxetags.tag.warrior"
    tags: [力量, 壁垒]
    recommended-tags: [力量, 壁垒, 物理]
```

### 8.2 四件饰品（注意：不写 tags）

```yaml
charms:
  dawn-helm:
    enabled: true
    material: 306            # 铁头盔
    display-name: "&e黎明头盔"
    lore:
      - "&7头盔护符"
      - "&8[护符槽位: 头盔]"
      - "&f防御: &c+8"
    type: 头盔
    equip-lore: "&8[护符槽位: 头盔]"
    rarity: RARE
    permission: qingcharms.charm.dawn-helm

  dawn-chest:
    enabled: true
    material: 307
    display-name: "&e黎明胸甲"
    lore:
      - "&7胸甲护符"
      - "&8[护符槽位: 胸甲]"
      - "&f生命值: &c+40"
    type: 胸甲
    equip-lore: "&8[护符槽位: 胸甲]"
    rarity: RARE
    permission: qingcharms.charm.dawn-chest

  dawn-greaves:
    enabled: true
    material: 308
    display-name: "&e黎明护腿"
    lore:
      - "&7护腿护符"
      - "&8[护符槽位: 护腿]"
      - "&f防御: &c+12"
    type: 护腿
    equip-lore: "&8[护符槽位: 护腿]"
    rarity: RARE
    permission: qingcharms.charm.dawn-greaves

  dawn-pendant:
    enabled: true
    material: 95
    data: 14
    display-name: "&a黎明吊坠"
    lore:
      - "&7项链护符"
      - "&8[护符槽位: 项链]"
      - "&f攻击力: &c+12"
    type: 项链
    equip-lore: "&8[护符槽位: 项链]"
    rarity: EPIC
    permission: qingcharms.charm.dawn-pendant
    requirements:
      player-level: 10          # 原版等级门槛，不是成长等级
      classes: [paladin]        # ★ 职业门槛：只允许圣骑士装备
```

> `dawn-pendant` 的 `requirements.classes: [paladin]`：非圣骑士装了会被拒。
> 等级门槛字段是 **`player-level`**（老文档写的 `character-level` / `class-level` 代码并不读）。

### 8.3 规则

```yaml
rules:
  dawn-bond-2:
    enabled: true
    name: "黎明初鸣"
    tags: [圣光]
    count: 2
    add-lore:
      - "&f防御: +5%"

  dawn-bond-4:
    enabled: true
    name: "黎明共鸣"
    tags: [圣光]
    count: 4
    add-lore:
      - "&f攻击力: +8%"

  paladin-oath:
    enabled: true
    name: "黎明守卫"
    classes: [paladin]
    tags: [守护]
    count: 3
    add-lore:
      - "&f生命: +5%"
```

### 8.4 玩家实际体验推演

| 玩家 | 装备件数 | 圣光 | 守护 | 黎明初鸣(2) | 黎明共鸣(4) | 黎明守卫(3, 守护) |
|---|---|---|---|---|---|---|
| 圣骑士 | 1 | 1 | 1 | ❌ | ❌ | ❌ |
| 圣骑士 | 2 | 2 | 2 | ✅ | ❌ | ❌ |
| 圣骑士 | 3 | 3 | 3 | ✅ | ❌ | ✅ |
| 圣骑士 | 4 | 4 | 4 | ❌ 被高档顶掉 | ✅ | ✅ |
| 战士 | 4 | 0 | 0 | ❌ | ❌ | ❌ |

关键点：**标签池由职业决定**。战士装的是一模一样的四件饰品，但他的职业标签是 `[力量, 壁垒]`，
所以「圣光」永远是 0，羁绊怎么装都不亮 —— 这就是职业专属构筑的实现方式，不需要额外加锁。

### 8.5 发一发，测一测

```
/qc reload
/qc list
/qc build                      ← 看当前职业、推荐标签、已激活羁绊（显示 name）
/qc build 期望输出：
  当前职业构筑推荐：
  当前职业：paladin
  契合特质：圣光, 守护, 治疗
  已激活羁绊：黎明初鸣, 黎明守卫        ← 这一行显示的就是规则里的 name
  （装到 4 件时变成：黎明共鸣, 黎明守卫 —— 黎明初鸣被高档顶掉）
/qc give dawn-helm
/qc give dawn-chest
/qc give dawn-greaves
/qc give dawn-pendant
```

验证点：
1. 圣骑士装到第 2 件 → `dawn-bond-2`（黎明初鸣）亮，构筑 Lore 里出现「防御: +5%」。
2. 装到第 4 件 → `dawn-bond-4`（黎明共鸣）亮，黎明初鸣**自动熄灭**（同档只亮最高档）。
3. 装到第 3 件 → `paladin-oath` 亮（`classes` + `守护` 计数 3）。
4. 换非圣骑士账号 → `dawn-pendant` 装不上，提示职业限制；羁绊全灭。

---

## 九、怎样才算"合理"的构筑

配置能跑起来只是及格。下面这些是**手感**层面的经验，按重要性排序。

### ① 先定槽位数，再定 `count`

你有 6 个槽 = 最多 6 件饰品，所以 `count` 的实际天花板就是 **6**。
一条构筑里主标签**不要超过 2 个**：标签越多，每件饰品贡献的点数越多，`count` 越绕（见 7.3 的倍数关系）。

**建议**：主标签 1 个（决定流派），想做细分再加 1 个副标签（如"守护"= 坦克向、"治疗"= 辅助向）。

### ② 阈值要"差一件就断"

反面教材：

```yaml
dawn-bond:
  tags: [圣光]
  count: 2          # 玩家随手 2 件就触发，毫无取舍
```

正面：

```yaml
dawn-bond:
  tags: [圣光]
  count: 5          # 6 个槽里必须放弃 1 个槽给别的功能
```

**阈值 = 你要玩家做的那个选择。** 不要设成"反正都会凑够"。

### ③ 职业本身就是最强的锁

标签池由职业决定：圣骑士只有 `圣光 / 守护`，战士只有 `力量 / 壁垒`。
所以**同一套圣光饰品，战士装上去一点羁绊都不亮**，不需要额外加锁。

想再加一道明确的门槛（比如"必须是圣骑士才吃这条加成"）就写 `classes: [paladin]`，
玩家装不上时也会有清晰的职业提示。

### ④ 属性用百分比，别用固定值

```yaml
add-lore:
  - "&f攻击力: +8%"      # ✅ 后期不膨胀
  # - "&f攻击力: +80"     # ❌ 后期废掉
```

### ⑤ 一条规则只给 1~2 行 Lore

规则的本质是"构筑奖励"，不是"属性堆叠器"。一次给 5 行属性，会让护符本身的属性变得毫无意义。

### ⑥ 千万别在多条规则里写同样的 Lore

去重机制会让它只生效一次：

```yaml
rule-a:
  add-lore: ["&f攻击力: +5%"]
rule-b:
  add-lore: ["&f攻击力: +5%"]     # ❌ 被去重，只有 +5%，不是 +10%
```

想要叠加就写成不同数值：`+5%` 和 `+3%`。

### ⑦ 取舍靠 `count`，不靠互斥

冲突（`block` / `suppress`）已经下线，饰品之间不会互相排斥。
想让玩家"只能二选一"，用槽位数量 + `count` 来做：

- 6 个槽，主羁绊要 5 件 → 只剩 1 个槽给副属性，天然有取舍。
- 两套羁绊各要 4 件 → 玩家必须选一边。

这样比硬锁手感好：玩家看得见"再少一件就断"，而不是被弹窗拦下来。

### ⑧ 命名规范（省未来的自己）

| 对象 | 建议 |
|---|---|
| 护符 ID | `dawn-helm`、`shadow-boots`（小写 + 连字符） |
| 规则 ID | `dawn-bond`、`dawn-shadow`（标签名 + 规则类型） |
| 职业 ID | `paladin`、`warrior`（英文，和其他插件对得上） |

规则 ID 只在日志、事件和 `/qc build` 里出现，但保持规范能让排错快十倍。

### ⑨ 一套完整构筑的"标准配方"

```
职业 tags: [圣光, 守护]        ← 标签池，决定这套是谁的
1 条 dawn-bond-2  (count: 2)   ← 装 2 件，入门档
1 条 dawn-bond-4  (count: 4)   ← 装 4 件，进阶档
1 条 dawn-bond-6  (count: 6)   ← 装满 6 件，毕业档
= 6 个槽，3 个档位，玩家每少一件都看得见差别
```

进阶再加一条 `classes: [paladin]` 的规则，给本命职业一点额外甜头。

---

## 十、成长系统最小配置（可选）

`progression.yml` 默认已开启。**只有 Lore 里带"可成长"字样的护符**才会进成长系统。

```yaml
progression:
  enabled: true
  activation-lore:
    - "可成长"        # 命中这行 → 启用成长，该行会被替换成成长状态 Lore
  lore:
    level: "&7等级: {level}/{max}"
    enhance: "&7强化: +{enhance}"
    breakthrough: "&7突破: {stage}阶段"
    star: "&7星级: {star}"
    awakened: "&7觉醒: 已觉醒"

level:
  enabled: true
  max-level: 10
  cost:                       # 每级消耗（可组合）
    wear-seconds: 60          # 累计佩戴秒数
    kills: 10                 # 击杀数
    experience: 20            # 原版经验
    vault: 0
    playerpoints: 0
  costs:
    5:                        # 第 5 级单独覆盖
      wear-seconds: 120
      kills: 25
      experience: 50
  unlock-lore:
    2:
      - "&f生命: +1%"         # 到达 2 级解锁这行 Lore（给 ILS 读）
    5:
      - "&f攻击力: +1%"
```

护符上这样写：

```yaml
lore:
  - "&7项链护符"
  - "&8[护符槽位: 项链]"
  - "&d可成长"                # ★ 激活成长
```

玩家命令（权限 `qingcharms.progression`）：

```
/qcgrow status            # 看当前状态
/qcgrow level             # 升级（按 cost 扣资源）
/qcgrow enhance           # 强化
/qcgrow breakthrough      # 突破（要求强化到当前上限）
/qcgrow star              # 升星
/qcgrow awaken            # 觉醒
```

要点：

- **手动操作才扣资源**，不会自动升级。
- 成长状态写在**物品 Lore** 里（不是隐藏数据），玩家能看到。
- 成长状态按**物品实例 ID** 保存，所以两件同款护符各有各的等级。
- `unlock-lore` 的 key 是**目标等级 / 突破阶段序号**，不是"从几级开始"。
- 突破的 `level-cap` / `enhance-cap` 是**突破后的新上限**。

---

## 十一、命令与权限表

| 命令 | 权限 | 说明 |
|---|---|---|
| `/qc help` | `qingcharms.command` | 帮助 |
| `/qc list` | `qingcharms.command` | 列出所有护符及启用状态 |
| `/qc reload` | `qingcharms.command.reload` | 重载全部配置 |
| `/qc status` | `qingcharms.command` | 存储状态、备份情况 |
| `/qc gui` / `/qcg` | `qingcharms.gui` | 打开装备 GUI |
| `/qc bag` / `/qcb` | `qingcharms.bag` | 打开护符背包 |
| `/qc open <玩家>` | `qingcharms.command.admin` | 管理在线玩家背包 |
| `/qc build` | `qingcharms.command` | 查看职业、推荐标签、已激活规则 |
| `/qc add <id> <物品|ID:data> [类型] [稀有度]` | `qingcharms.command.admin` | 新增护符 |
| `/qc set <id> <字段> <值>` | `qingcharms.command.admin` | 改字段（material/data/type/equip-lore/rarity/name/permission/enabled/lore） |
| `/qc on|off <id>` | `qingcharms.command.admin` | 启用 / 禁用 |
| `/qc remove <id>` | `qingcharms.command.admin` | 删除护符 |
| `/qc give <id> [玩家]` | `qingcharms.command.give` | 发放护符物品 |
| `/qc export <id>` | `qingcharms.command.admin` | 把手中物品导出到 `qcItems/` |
| `/qcgrow ...` | `qingcharms.progression` | 成长操作 |

> `/qc give` 会检查**目标玩家**是否拥有该护符的 `permission`，没有就拒绝发放。所以护符的 `permission` 默认是 `qingcharms.charm.<id>`，用权限组插件记得给玩家这个节点（或 `qingcharms.charm.*`）。

### `/qc reload` 的一个副作用（要知道）

重载后，插件会**重新校验所有在线玩家身上已装备的护符**。
如果改完 `type`、`lore`、`permission`、`requirements` 后某件不再合法：

- 会自动卸下并放回背包；
- 背包满则保留在身上并提示。

**所以别在人多的时候乱改已发放护符的识别字段。** 建议先在测试号上验证。

### 防伪机制（新手最容易懵的一条）

`settings.require-charm-authentication: true` 时，`/qc give` 发出的每件护符都带服务端 HMAC 签名。
以下物品**不会被识别**：

- 创造模式拿的同款玻璃
- 别的插件（含 MMOItems）复制的同外观物品
- 你自己用 NBT 编辑器拼出来的
- 改了签名密钥之前发的老物品

**唯一正确姿势**：先配好护符定义 → `/qc give` 发给玩家。
迁移老存档时，要么重新发放，要么临时 `require-charm-authentication: false`（此时防伪失效，迁移完立刻改回来）。
`charm-auth-secret` 要备份，**换密钥 = 所有旧签名失效**。

---

## 十二、排错对照表

| 症状 | 最可能的原因 | 怎么修 |
|---|---|---|
| `/qc list` 里没有我的护符 | `enabled` 没写 `true` | 补 `enabled: true` + `/qc reload` |
| 有护符，但右键装不上 | 槽位 `type` 与护符 `type` 不完全一致 | 逐字核对（大小写不敏感，空格敏感） |
| 提示"槽位类型不匹配" | GUI 里没有该 `type` 的 `action: equip` 槽 | 在 `gui.yml` 加同 `type` 的槽 |
| 提示缺少 Lore / 无法识别 | `display-name` 或 `lore` 和物品对不上 | 只保留稳定锚点行，或改用 `equip-lore` |
| 世界右键 / 原版护甲槽装不上 | **设计如此** | 只能在 `/qc gui` 里装 |
| 装上了但没属性 | `add-lore` / 护符 Lore 格式 ILS 不认 | 对照 ILS 的属性名格式，别自己造 |
| 构筑规则不生效 | `enabled: false` 没删；或标签数差 1 | `/qc build` 看已激活规则和标签数 |
| 羁绊明明够了却不触发 | `count` 是**全部 tags 求和**；或 GUI 里实际没装够那么多件 | 用 `/qc build` 核对实际计数 |
| 羁绊怎么都不亮 | 玩家职业和规则的 `tags` 不沾边（标签池由职业决定） | 核对 `classes.<id>.tags` 和玩家权限 |
| 控制台提示 `type` 已废弃 | 规则里还留着旧的 `type:` | 删掉那行，规则照常按 bond 生效 |
| 成长护符不升级 | Lore 没有 `activation-lore` 文字 | 加 `&d可成长` 之类的一行 |
| 成长解锁 Lore 没出现 | `unlock-lore` 的 key 写错 | key 是目标等级（如 `2:`） |
| `/qc give` 提示目标无权限 | 目标缺 `qingcharms.charm.<id>` | 给权限节点 |
| 改了配置没反应 | 忘了 `/qc reload`，或 YAML 缩进坏了 | 重载看日志；YAML 用空格不用 Tab |
| 重载后玩家护符掉背包 | 改了识别字段导致不合法 | 见上文"reload 副作用"，属预期 |

**调试点：**

```
/qc list          → 确认护符加载了、状态是"启用"
/qc build         → 确认职业、标签数、已激活规则
服务端日志        → 搜 QingCharms，槽位越界/规则非法都会 warning
```

坏掉的规则**不会让插件崩**，只会在日志里写 `Ignoring invalid build rule 'xxx'`，然后**静默跳过**——所以"规则不生效"第一件事就是去看日志。

---

## 十三、上线前检查清单

**护符**

- [ ] 每件都有 `enabled: true`
- [ ] `type` 和某个 GUI 槽位的 `type` 完全一致
- [ ] `equip-lore` 那一行确实在物品 Lore 里
- [ ] `display-name` / `lore` 只写了稳定不变的行
- [ ] `permission` 已配到权限组（或给了 `qingcharms.charm.*`）
- [ ] 用 `/qc give` 实测过能装、能卸、能替换

**GUI**

- [ ] `slot` 编号都在 `0 ~ rows*9-1` 内，无重复
- [ ] 有 `action: close` 的退出按钮
- [ ] 每个 `equip` 槽的 `type` 都能在护符里找到对应

**构筑**

- [ ] 每条规则都显式写了 `enabled`
- [ ] 没有两条规则写同样的 `add-lore` 文本
- [ ] `count` 用 `/qc build` 实测过（计数 = 已装备件数 × 命中的职业标签数）
- [ ] 没有规则残留 `type` / `mode` / `suppress-rules`
- [ ] 饰品配置里没有残留 `tags`
- [ ] `count` 设计过"差一件"的手感

**数据**

- [ ] 备份了 `charm-auth-secret`
- [ ] 知道 `playerdata/` 和 `DataBacked/` 不要手动改
- [ ] 上线前在测试号上跑了一遍 `/qc reload`

---

## 附：默认值速查

| 项 | 默认值 |
|---|---|
| `charms.<id>.enabled`（config.yml） | **false** |
| `qcItems` 文件 `enabled` | **true** |
| `rules.<id>.enabled` | **true** |
| `rule.count` | 2 |
| `rule.classes` | 空 = 不限职业 |
| 护符 `rarity` | COMMON（写错也是 COMMON） |
| 护符 `category` | 等于 `type` |
| 护符 `level` | 0 |
| 护符 `permission` | `qingcharms.charm.<id>` |
| `gui.row` | 3（可用 `rows`） |
| `charm-bag.page-size` | 45 |
| `settings.require-charm-authentication` | true |
| `trigger-message.cooldown-ticks` | 5 |

---

**配错了不可怕，QingCharms 的容错其实很好**：坏槽位跳过、坏规则跳过、坏 YAML 会回滚到旧配置并提示（`/qc reload` 失败时继续用旧配置）。

真正会让你怀疑人生的只有三件事：

1. `enabled` 默认值不同（config 是 false，qcItems 是 true）；
2. `lore` 是子集匹配，多写一行就识别不到；
3. 构筑效果 Lore 的格式是 ILS 的，写错静默失效。

把这三条记住，剩下的都是照模板填空。
