# V1.0.4 输入 BUG 审核报告

## 1. 问题摘要

- **现象**：配置界面中的"网络名称"输入框无法输入字母、中文等文字，只能输入数字。
- **实际影响范围**：Fabric 1.0.0 – 1.0.3 的全部配置输入框（网络名称、中继服务器、网络密码、网卡名、虚拟端口）。
- **严重级别**：严重（配置界面核心功能不可用，网络名称无法使用常规文本）。
- **结论**：已定位、已修复，并在 V1.0.4 两个平台产物中生效。

## 2. 根本原因

Fabric 端的 `PlaceholderTextFieldWidget` 重写了 `TextFieldWidget#write(String)`：

```java
@Override
public void write(String text) {
    // 只允许输入数字
    String filtered = text.replaceAll("[^0-9]", "");
    if (!filtered.isEmpty()) {
        super.write(filtered);
    }
}
```

该组件被配置界面的**全部五个输入框**复用：

- `networkNameField`（网络名称）
- `relayServerField`（中继服务器）
- `networkSecretField`（网络密码）
- `devNameField`（网卡名）
- `virtualPortField`（虚拟端口）

`write` 是字符、粘贴等输入的统一入口。由于过滤逻辑没有开关、且对所有字段生效，字母与中文（包括输入法上屏的字符）在写入前就被 `replaceAll` 删除。**这与输入法、键盘布局、系统区域设置无关**，是组件层的全局过滤缺陷。

## 3. 修复方案

### Fabric

- 删除 `write(String)` 重写，恢复 `TextFieldWidget` 原生输入路径；
- 新增 `setNumericOnly(boolean)`，通过 `setTextPredicate` 精确控制数字过滤；
- 构造函数显式设置 `setTextPredicate(value -> true)`，默认接受所有字符；
- 仅 `virtualPortField` 调用 `setNumericOnly(true)`。

### Forge

Forge 1.0.3 的编译产物核对结果为：`setNumericOnly(true)` 只作用于 `virtualPortField`，文本字段未受全局过滤影响；但其数字限制依赖 `insertText` 重写，`setValue`、粘贴等路径并不统一。V1.0.4 一并加固：

- 删除 `insertText` 重写；
- 改用 `EditBox#setFilter(Predicate<String>)`，所有写入路径统一遵循字段过滤；
- 默认 `filter = null`（不限制），仅虚拟端口设置 `[0-9]*`；
- 保留 `isNumericOnly()` 便于后续界面逻辑判断。

## 4. 修复验证

### 静态核对（本次构建产物）

- `javap` 确认 Fabric 构建产物 `PlaceholderTextFieldWidget` 不再包含 `write`/`method_1888` 重写；
- `javap` 确认 Forge 构建产物不再包含 `insertText` 重写，数字过滤通过 `setFilter` 完成；
- 配置界面仅 `virtualPortField` 调用 `setNumericOnly(true)`；
- 全仓库检索 `[^0-9]`、`isDigit`、`write(`、`setTextPredicate`、`setFilter`，未发现其他 UI 全局字符过滤；
- 两个平台 `gradlew build` 均构建成功，产物内版本号为 `1.0.4`。

### 预期行为（需游戏内冒烟确认）

| 字段 | 字母/中文 | 数字 | 符号 | 说明 |
| --- | --- | --- | --- | --- |
| 网络名称 | 允许 | 允许 | 允许 | 最长 32 字符 |
| 中继服务器 | 允许 | 允许 | 允许（`:` `.` `/` `-`） | 最长 128 字符 |
| 网络密码 | 允许 | 允许 | 允许 | 最长 128 字符 |
| 网卡名 | 允许 | 允许 | 允许 | 最长 32 字符 |
| 虚拟端口 | 拒绝 | 允许 | 拒绝 | 最长 5 位，仅 0-9 |

## 5. 同类风险排查

- `/easytier` 命令中的 `name.matches("[a-zA-Z0-9_-]+")` 是服务端命令参数校验，不影响 UI 输入，保留。
- `FileSanitizer` 只处理保存到磁盘的文件名，不改变输入框显示内容。
- `Integer.parseInt` 只用于虚拟端口/端口转发参数，异常路径均有提示，不会静默吞字符。
- 未发现其他 `EditBox` / `TextFieldWidget` 子类或字符级过滤逻辑。

## 6. 回归测试建议

1. Fabric 1.0.4：网络名称输入 `my-mc-network`、`测试网络`、`abc123`，应完整显示。
2. Forge 1.0.4：同样输入上述内容，并测试窗口缩放、从状态面板返回后内容是否保留。
3. 虚拟端口输入字母、符号应被拒绝，输入 `25565` 正常。
4. 粘贴含字母的文本到网络名称，应完整粘贴；粘贴到虚拟端口只保留数字。
5. 保存配置、切换配置、删除配置后再次加载，字段内容应正确回填。
6. 使用中文输入法上屏网络名称，确认候选词与上屏字符均能进入输入框。

## 7. 产物

- `easytier_go-forge-1.0.4.jar` / `easytier-go-fabric-1.0.4.jar`
- 对应 `-sources.jar` 与 `-source.zip`
- 校验值见 `SHA256SUMS.txt`
