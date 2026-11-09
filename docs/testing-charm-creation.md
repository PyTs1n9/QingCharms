# 饰品制作链路验收

范围以 [制作链路决策](decisions-charm-creation.md) 为准。自动测试使用 Bukkit 接口测试替身，不启动服务器；以下实服步骤需在 Spigot 1.8.8 和 1.12.2 分别执行，不能用单元测试通过替代。

## 自动验证

```text
mvn clean test
mvn test
mvn package
```

- `CharmTemplateServiceTest`：最简模板、Lore 唯一类型、覆盖规则、完整元数据、堆叠交付、容量限制、写盘/读回/部分背包写入/提交失败及恢复失败。
- `CharmTemplateUpdateTest`：真实管理器的单目标更新、HMAC 签名和实例唯一性、旧模板及中文 ID、嵌套重复 ID、配置优先级、无关坏 YAML 隔离、异步重载与回滚修订保护。
- `ItemIdentityLoreTest`：签名不改变普通 Lore 及排版空行，无可见 Lore 时共用一行隐藏身份信息。
- `CharmTypeInferenceTest`：零/多/重叠类型拒绝，忽略没有可见文字的类型。

## 实服操作

1. 用 Lore 工具制作物品：Lore 含唯一一个已配置的 GUI 槽位 `type`，另加 ILS 属性行、空行和附魔。显示名可含另一个槽位类型，确认它不影响类型判定。
2. 执行 `/qc export creation_test`。确认手上立即成为签名单件，`qcItems/creation_test.yml` 只含 `Item`、`charm.enabled`、`charm.permission`，不含旧 UUID、认证令牌或 `charm.type`。
3. 不重载，执行 `/qc check`、`/qc give creation_test`，确认认证和 ID 正确，两个实例 UUID 不同。打开 `/qc gui` 后在背包右键装备，再卸下核对完整物品。
4. 只在显示名写类型、Lore 不写类型时拒绝导出；Lore 含多个类型时同样拒绝，且不产生文件和签名单件。
5. 相同 ID 再次导出默认拒绝；`-f` 覆盖后，按 ID 发放应得到新附魔和 Lore。手工限制会被最简模板替换，需要重新填写后重载。
6. 测试一叠物品：成功时签名单件留手，其余数量回普通背包；普通背包满而护甲/副手为空时仍应拒绝，物品数量与文件均不变，地面无掉落。
7. 在启用的 `config.yml` 定义同 ID，确认即使 `-f` 也拒绝。再在两个子目录放入同名模板，确认重复 ID 也拒绝，两个文件均不改。
8. 改坏一个无关模板 YAML，再导出另一个 ID；本次导出和发放应成功。恢复坏 YAML 后重载，确认导出结果仍可识别；无关手工修改只在显式重载后生效。
9. 重启服务器，核对已导出模板和此前签名仍可识别，已有玩家装备与成长记录可读取。

分别在未安装 ILS、已安装 ILS 的环境执行制作、发放及 GUI 操作。安装 ILS 时核对其收到的完整物品 Lore、属性名与数值格式；QingCharms 不在制作链路中计算或应用战斗属性。
