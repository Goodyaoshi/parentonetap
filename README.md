# 爸妈一键通（ParentOneTap）

> 给七八十岁爸妈的安卓 App：**一键发位置到微信 / 一键打车回家 / 一键打电话 / 紧急求助**。
> 子女一次配置，零服务器。Slogan：**爸妈点一下，我就知道啦**。

---

## 项目简介

- **零服务器**：没有后端、没有账号、不做远程配置。
- **日常功能零网络请求**：定位、发位置、打车、打电话全部本地完成，位置数据不出手机。
  全 App 唯一的联网点是设置页解析地图分享**短链**（见下文）。
- **权限仅 3 个**：`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`、`INTERNET`
  （不申请 `CALL_PHONE`，统一走系统拨号；无后台定位、无通知权限）。
- **老年友好**：4 个特大按钮（字号 32sp 起）、全屏大字反馈、桌面小组件免开 App。

## 四大功能

| 功能 | 说明 |
| --- | --- |
| 发位置 | 定位（10s 超时）→ 生成高德/百度 https 链接 → 拉起微信分享；发到微信给家人 |
| 打车回家 | 目的地可选「家/常用地点」，三种方式：**导航**（高德步行路线页 → 腾讯步行路线页）→ **顺风车** → **打车**（高德驾车路线页 → 腾讯驾车路线页，页内点打车/顺风车）；同样需手机定位已开启（地图要先知道人在哪），未开会先引导打开 |
| 打电话 | 联系人磁贴（大字关系称呼），点按 → 系统拨号盘预填号码（防口袋误拨） |
| 紧急求助 | 点击**直接跳系统拨号盘**拨打紧急联系人（默认联系人第 1 位；农村场景子女/邻居优先），未配置兜底 120 |

隐藏配置入口：首页标题「爸妈一键通」**连点 5 次**，进隐藏设置页（子女用）。

## 环境要求

- JDK 17 及以上（项目 `sourceCompatibility`/`targetCompatibility` = 17）
- Android SDK：compileSdk / targetSdk 35，minSdk 26
- Gradle 8.11.1（wrapper 走腾讯云镜像，见 `gradle/wrapper/gradle-wrapper.properties`）
- 国内镜像：`settings.gradle.kts` 中阿里云镜像优先
- `local.properties` 指向本机 SDK，例如 `sdk.dir=D:\\Android\\android-sdk`（不入库）
- ABI：仅 `arm64-v8a`

## 构建命令

```bat
:: Debug 包（包名带 .debug 后缀）
.\gradlew.bat assembleDebug

:: Release 包（R8 混淆 + 资源收缩）
.\gradlew.bat assembleRelease
```

产物路径：
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

首次构建或遇到 `kspCaches` / configuration-cache 锁冲突时：

```bat
.\gradlew.bat --stop
:: 结束残留 java 进程，删除 app\build\kspCaches 后重试
```

## 签名说明

Release 签名从根目录 `keystore.properties` 注入（**不入库**），模板见 `keystore.properties.example`：

```properties
storeFile=parentonetap-release-key.jks
storePassword=******
keyAlias=parentonetap-release
keyPassword=******
```

该文件不存在时自动**回退 debug 签名**，保证 `assembleRelease` 始终能出包。

## 子女初始化清单（一次性，回老家或视频遥控）

1. 编译 release APK 装到爸妈手机（adb 或微信传文件安装）。
2. 打开 App → 按「家人协助设置」三步引导：**授权定位 → 试发位置 → 完成**。
3. 连点标题 5 次进设置页 → 点「**设当前位置为家**」（手机在家时一键完成）、
   添加联系人电话、确认打车渠道开关。
4. 点「发位置」→ 微信选家庭群发送，确认家人点开链接地图落点正确。
5. 点「打车回家」→ 确认地图拉起且目的地正确（手机定位需已打开）；不灵就回设置页调渠道开关。
6. 把「发位置」「打车回家」两个小组件拖到桌面首屏。

## 配置项要点

- **家的位置**：主推「设当前位置为家」（手机在家时一键完成，无坐标系问题）；
  备选粘贴地图分享链接或坐标。**短链**（如 `https://surl.amap.com/xxx`）会自动联网跟随跳转解析；
  **长链**（`uri.amap.com/marker?...`、`api.map.baidu.com/marker?...`）本地直接解析。
  注意：**不要用百度地图直接复制的坐标**（BD-09 会偏几百米）——但百度**分享的链接**没问题。
- **地图服务商**（多选）：勾选哪几家，发位置消息就带哪几家的 https 链接，默认仅高德。
  发位置链接不含腾讯地图（其 marker 接口 `referer` 需注册 Key，无 Key 静默失败）；腾讯地图仅在「导航 / 打车」链路中作为兜底渠道保留。
- **坐标系**：统一 GCJ-02；GPS 原始 WGS84 结果会做标准转换，避免偏移。

## 打车渠道真机验证结论

真机（小米 24115RA8EC）用 `adb shell am start` 拉起 + 截图 + `dumpsys package com.autonavi.minimap`
读 intent-filter 三重验证：

| 写法 | 结果 |
| --- | --- |
| `amapuri://drive` | ❌ 只落到首页地图 |
| `amapuri://taxi` | ❌ 只落到首页 |
| `amapuri://openFeature?featureName=Taxi` | ❌ 只落到首页 |
| `amapuriucar://` + `com.ucar.intent.action.UCAR` | ❌ 只落到首页 |
| `INTENT_ACTION_TAXISHORT` on NewMapActivity | ❌ 只落到首页 |
| **`amapuri://route/plan/?...&t=0`** | ✅ **打开驾车路线页（目的地=家），顶部有「打车 / 顺风车 / 公共交通」标签** |

- 高德把**所有** `amapuri://` 交给同一个 `SchemeHandleActivity` 兜底分发，路径不认识就忽略或提示「不支持功能」。
- **高德没有公开的「直达打车页 / 顺风车页」deeplink**，最深只能到驾车路线页再由页内入口进入，
  因此现有实现（`t=0` + 完整参数）即为最优解。
- `route/plan` 的 `t` 参数（官方定义）：驾车 0 / 公交 1 / 步行 2 / 骑行 3 / 火车 4 / 长途客车 5。
  导航默认步行（`t=2`），打车/顺风车用 `t=0`，页内再点「打车」/「顺风车」。
- 中文参数（`dname`/`to`）必须 `Uri.encode`。
- 腾讯地图：`qqmap://map/routeplan?type=drive|bus|walk|bike`，页内再点打车。

> 排查提示：用 `adb shell` 手工测 URI 时，**必须给 URI 加引号**，
> 否则 `&` 会被 shell 当命令分隔符，链接在第一个 `&` 处被截断，表现为「参数不生效」。

## 真机验收要点

- [ ] `assembleDebug` / `assembleRelease` 通过
- [ ] App 权限仅 3 个：两个定位 + INTERNET
- [ ] 室内能定位成功（network provider），记录耗时与精度
- [ ] 发位置 → 微信发送 → 家人手机点开链接，标记点落点正确（重点验证 GCJ-02 无偏移）
- [ ] 断网/飞行模式时发位置 → 走上次位置并带「N分钟前」标注；从未定位成功时提示「定位失败，请到窗边空旷处再试」
- [ ] 定位开关未开时 → 「发位置」与「打车回家」都提示「手机定位没打开」+「去打开定位开关」按钮，返回 App 自动继续
- [ ] 设置页粘贴高德分享短链 → 自动解析出名称+坐标；断网时给出明确失败提示
- [ ] 导航回家默认步行路线（页内可切公交）；打车/顺风车进高德后不报「不支持功能」
- [ ] 卸载高德时打车降级到 95128 拨号
- [ ] 设置页「设当前位置为家」→ 打车目的地正确；改联系人 → 打电话页同步变化
- [ ] 桌面小组件两条链路可用
- [ ] 爸妈视角演练：不讲解，看能不能自己完成「发位置给家人」和「打车回家」

## 明确不做（v1）

- 后台定位 / 实时位置上报 / SOS 主动通知子女 / 远程改配置（都需要网络通道或服务器）。
  替代：临时实时定位直接用微信自带的「共享实时位置」。
- 微信卡片分享（需微信开放平台 AppId，维护成本高）；改为纯文本链接，微信里自动识别为可点击链接。
- 聊天式点对点分享要把家庭群置顶，分享面板第一步即可选到。
