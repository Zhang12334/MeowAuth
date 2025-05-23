# MeowAuth

**MeowAuth** 是一个轻量级、高效的反盗版库

它通过与自定义验证服务器通信，确保您编写/改写的插件所在服务器运行在被授权的 IP 地址和环境下

## 功能特点

- **反盗版验证**：启动时向指定验证服务器发送请求，获取合法 IP 地址，防止盗版服务器运行

- **动态 IP 检查**：每 5 分钟通过外部 IP 查询服务（如 `ip.sb`、`4.ipw.cn`）验证本机 IP 是否与授权 IP 匹配

- **安全关闭机制**：如果验证失败（401）或 IP 不匹配超过 5 次，自动关闭服务器，确保安全

- **简单集成**：只需几行代码即可嵌入插件, 简单快捷

- **轻量高效**：异步网络请求，不会阻塞服务器主线程

- **内置配置**：无需外部配置文件, 数据实时获取, 防止篡改

- **可自定义配置**：阈值可配置, 小白也可轻松使用

## 使用场景

- **插件开发者**：保护你的付费插件, 在添加混淆的情况下可有效防止被盗版服务器非法使用

- **服务器开发**：在改写开源插件时可自行添加此库并进行混淆, 避免定制内容泄露

## 安装与使用

### 1. 配置验证服务器

- 部署一个 `HTTPS` 服务器用于处理验证请求, 并返回 JSON 响应（见下文）

验证服务器需支持 HTTPS 并处理以下请求：

- **请求**：`GET /verify?port=<port>`

- **响应**：
  - 成功: 返回 200 状态码, Body(JSON): `{ "requestIP": "x.x.x.x" }`
  - 失败: 返回 401 状态码, Body(JSON): `{ "requestIP": "x.x.x.x" }`

示例服务器端逻辑 (Node.js):

```javascript
const express = require('express');
const app = express();

// 启用信任代理，处理 X-Forwarded-For
app.set('trust proxy', true);

// 定义任意接口用于处理验证
app.get('/verify', (req, res) => {

    // 获取插件安装的服务端的服务端口号
    const { port } = req.query;

    // 优先从 X-Forwarded-For 获取 IP，若无则回退到 req.ip
    const requestIP = normalizeIP(getClientIP(req)); 

    // 验证请求
    if (!isValidServer(requestIP, port)) {
        // 验证失败, 返回 401
        console.log(`401 Unauthorized | [${requestIP}] 服务器端口号 ${port} 验证失败`);
        return res.status(401).json({ requestIP });
    }

    // 验证成功
    console.log(`200 OK | [${requestIP}] 服务器端口号 ${port} 验证成功`);
    res.status(200).json({ requestIP });

});

// 从 X-Forwarded-For 获取 IP
function getClientIP(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) {
        const ip = forwarded.split(',')[0].trim();
        if (ip) return ip;
    }
    return req.ip;
}

// 规范化 IP 地址
function normalizeIP(ip) {
    ip = ip.trim();
    if (ip.startsWith('::ffff:')) return ip.split('::ffff:')[1];
    if (ip === '::1') return '127.0.0.1';
    return ip;
}

function isValidServer(requestIP, port) {
    // 实现你的判断逻辑, 如匹配数据库内的合法IP列表
    if (requestIP === '127.0.0.1') return true;
}

app.listen(3000, () => console.log('验证服务器已在 3000 端口启动'));
```

### 2. 集成到插件

1. 将 `MeowAuth.java` 放入你的插件源码: [MeowAuth.java](https://github.com/Zhang12334/MeowAuth/blob/main/MeowAuth.java)

2. 在插件的 `onEnable` 方法中初始化并启动验证：

```java
package com.yourpackage.myplugin;

import com.yourpackage.MeowAuth; // 此处应与 MeowAuth.java 所在包名相同

import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // 初始化 MeowAuth
        String authServerUrl = "https://your-auth-server.com/";
        MeowAuth auth = new MeowAuth(this, authServerUrl);

        // 启动验证
        auth.startVerification();

        // 继续处理插件的加载
        getLogger().info("插件正在加载...");
    }
}
```

## 许可

请查看 [LICENSE.md](https://github.com/Zhang12334/zhcn_opensource_sharing_license?tab=License-1-ov-file)

对于本项目的追加条款：
- **允许** 在任何项目中使用本项目的源代码。
- **禁止** 出售本项目的源代码。
