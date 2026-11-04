# halo-plugin-regverify

在 Halo 的注册页加入一个可以在后台配置的验证入口：访客在弹窗中输入内容，插件用**后台配置的 JavaScript 脚本**校验输入，只有脚本返回通过时才允许提交注册。

- 适用于 Halo **2.22.0 及以上**（注册页在 2.20 改为服务端模板，设置中的 `code` 编辑器从 2.22 起提供）。
- 无 Console/UC 前端构建产物，全部配置通过插件设置完成。
- 脚本内容默认留空；脚本为空时插件不会拦截注册。

## 功能

- 在 `/signup` 注册表单中注入一个「开始验证」按钮和验证状态提示。
- 点击按钮打开弹窗，弹窗标题、说明、输入框标题/占位文字、按钮文字、成功/失败提示都可以在「插件设置 → 注册验证设置」中编辑。
- 后台使用 FormKit `code` 编辑器（JavaScript 高亮）编辑校验脚本。
- 验证通过后按钮旁的状态变为成功；未通过时停留在弹窗并显示失败原因。
- **服务端强制校验**：直接 POST `/signup` 绕过页面也会被拦截，未通过时 303 跳回 `/signup?regverify=failed`。
- 验证接口和注册提交都按客户端 IP 限流（10 次/分钟）。

## 安装

1. 构建：

   ```bash
   ./gradlew clean build
   ```

   产物位于 `build/libs/halo-plugin-regverify-1.0.0.jar`。

2. 在 Halo Console 的「插件 → 安装」上传 JAR，安装后启用。
3. 进入插件详情 →「设置」，填写并启用注册验证。

## 配置说明

| 设置项 | 说明 |
| --- | --- |
| 启用注册验证 | 关闭时注册页不会出现验证入口，注册也不会被拦截 |
| 验证入口按钮文字 / 弹窗标题 / 弹窗说明 | 注册页按钮与弹窗文案 |
| 输入框标题 / 占位文字 | 弹窗中输入框的文案 |
| 验证提交按钮文字 / 取消按钮文字 | 弹窗按钮文案 |
| 验证通过提示 / 未验证状态提示 / 验证中状态提示 | 状态提示文案 |
| 验证失败提示 / 输入为空提示 / 请求过于频繁提示 | 失败场景文案 |
| 脚本超时时间（毫秒） | 100–10000，默认 2000；超时按失败处理 |
| 验证脚本 | JavaScript 源码，留空表示未配置 |

> 启用开关只有在**脚本非空**时才会真正生效。这是为了避免「启用了插件但还没写脚本」把所有访客挡在注册页外。

## 脚本约定

脚本在服务端由 [Rhino](https://github.com/mozilla/rhino) 执行，可用变量：

| 变量 | 说明 |
| --- | --- |
| `input` | 访客在弹窗中输入的内容（已 trim） |
| `username` | 注册表单中的用户名 |
| `email` | 注册表单中的邮箱 |
| `displayName` | 注册表单中的显示名称 |
| `ip` | 客户端 IP |

返回值规则：

| 返回值 | 结果 |
| --- | --- |
| `true` | 通过，允许注册 |
| `false` / `null` / `undefined` / `""` | 拒绝，显示「验证失败提示」 |
| 其他非空字符串 | 拒绝，并把该字符串作为失败原因显示给访客 |
| 数字 | 非 0 通过，0 拒绝 |
| 抛出异常 / 超时 | 拒绝 |

示例：

```javascript
// 固定口令
return input === 'halo-2026';

// 根据用户名做不同校验
if (!username) {
  return '用户名不能为空';
}
return /^[a-z0-9_]{4,20}$/.test(input);

// 简单的数学题
return Number(input) === 7;
```

沙箱限制：

- 脚本**不能**访问 Java 类、文件、网络或其他进程，只能做纯计算。
- 脚本和输入长度分别限制为 20 KB / 2000 字符。
- 脚本执行有时间上限（默认 2 秒），死循环会被强制终止。
- 脚本的内容不会发送到浏览器，浏览器只拿到配置文案。

## 实现说明

| 组件 | 作用 |
| --- | --- |
| `SignupVerificationWebFilter` | `AdditionalWebFilter`：GET `/signup` 注入验证入口；POST `/signup` 重新执行脚本校验 `regverifyAnswer` |
| `SignupPageResponseDecorator` | 缓存注册页 HTML，在 `</body>` 前插入 CSS/JS/配置 |
| `SignupPageInjector` | 生成注入片段；所有文案按 JSON 转义（`<`、`>`、`&` 转为 `\u00xx`），避免脚本标签逃逸 |
| `RegVerifyEndpoint` | 公开接口 `POST /apis/api.regverify.halo.run/v1alpha1/verify`，供弹窗预校验 |
| `ScriptEvaluator` | Rhino 沙箱执行脚本，`ClassShutter` 拒绝 Java 访问，指令观察器实现超时 |
| `VerificationRateLimiter` | 基于 Resilience4j 的按 IP 限流 |
| `extensions/role-template-anonymous.yaml` | 把 `verify` 的 `create` 权限聚合到 `anonymous` 角色，注册页才能匿名调用 |
| `extensions/reverseproxy.yaml` | 把 `static/` 暴露为 `/plugins/regverify/assets/res/**` |
| `extensions/settings.yaml` | 插件设置表单（FormKit Schema） |

### 为什么用 HTML 注入

Halo 从 2.20 起把 `/signup` 改为服务端渲染的网关页面（`templates/signup.html`），插件目前没有针对网关页面的模板扩展点，只有主题可以覆盖模板。为了让插件不依赖主题改造，这里使用 `AdditionalWebFilter` + `ServerHttpResponseDecorator` 在响应写入前插入一小段 HTML。

这意味着：

- Halo 注册页的 DOM 发生变化时（例如按钮结构变化），脚本会尽力降级：找不到表单或提交按钮时不注入/不拦截，服务端校验仍然有效。
- 插件升级 Halo 大版本后建议回归注册流程。

### 服务端校验流程

1. GET `/signup`：如果插件已启用且脚本非空，注入按钮、隐藏字段 `regverifyAnswer` 和客户端脚本。
2. 访客在弹窗中提交：`POST /apis/api.regverify.halo.run/v1alpha1/verify`，服务端执行脚本并返回 `{ valid, message }`。
3. 验证通过后，客户端把输入写入隐藏字段，允许提交注册表单。
4. POST `/signup`：过滤器读取表单中的 `regverifyAnswer`，**再次执行脚本**；失败则跳回 `/signup?regverify=failed`，成功才放行到 Halo 的注册逻辑。

因为服务端不信任浏览器，脚本必须能在拿到原始输入时独立判断是否通过；前端校验只是即时反馈。

## 开发

```bash
./gradlew test        # 单元测试（脚本沙箱、HTML 注入、配置转义）
./gradlew build       # 构建插件 JAR
./gradlew haloServer  # 使用 DevTools 启动本地 Halo（需要 Docker）
```

本地联调也可以使用 Halo 的 `development` 运行模式：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixed-plugin-path:
      - /path/to/halo-plugin-regverify
```

## 已知限制

- 未在真实 Halo 实例上做端到端冒烟测试（当前开发环境无法启动 Halo 容器），已通过单元测试覆盖脚本沙箱、响应注入和配置转义；首次部署请按下面的清单验证。
- 注册页注入依赖 Halo 2.20+ 的网关模板结构，`spec.requires` 设为 `>=2.22.0`。
- 脚本由超级管理员维护，属于受信任代码；沙箱用于防止输入触发意外行为，不用于防御恶意管理员。

## 部署后检查清单

1. 插件启用后访问 `/signup`，确认出现「开始验证」按钮。
2. 填写错误内容，确认无法通过且提示正确；直接 POST 表单确认会被跳回 `/signup?regverify=failed`。
3. 填写正确内容，确认可以正常注册。
4. 关闭「启用注册验证」后确认按钮消失、注册不再被拦截。
5. 在窄屏/移动端检查弹窗布局。

## License

AGPL-3.0
