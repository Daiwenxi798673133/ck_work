# 区域性别及人群流入流出离线分析

> ClickHouse + Spring Boot + ECharts 离线人口流动分析看板

基于模拟的运营商信令数据，用 ClickHouse 做区域人口流动离线分析，通过 Spring Boot REST API 对外提供查询，前端用原生 ECharts 渲染各区域的人口流入流出趋势、OD 流向矩阵、人群画像分布。

数据范围设定在上海市（city_code=310000），覆盖陆家嘴、人民广场、徐家汇 3 个区域、8 个基站小区，1 万用户在 2026-05-31 至 2026-06-07 共一周的轨迹。

---

## 目录

- [架构概览](#架构概览)
- [目录结构](#目录结构)
- [一键运行指南](#一键运行指南)
- [API 一览](#api-一览)
- [文档索引](#文档索引)
- [技术栈](#技术栈)
- [局限说明](#局限说明)

---

## 架构概览

```
                            模拟信令数据
                                 │
                 ┌───────────────┴───────────────┐
                 │   DataGenerator（Java 内置）    │
                 │   seed=20260601, 1 万用户       │
                 └───────────────┬───────────────┘
                                 │ JDBC 批量写入
                                 ▼
        ┌────────────────────────────────────────────┐
        │              ClickHouse (库 flow)            │
        │  维度表 dim_region / dim_cell / dim_user...  │
        │  明细表 dwd_cell_imsi_5min（5 分钟切片）      │
        │  派生表 dws_user_window_loc（小时/天定位）    │
        └────────────────────────┬───────────────────┘
                                 │ 分析 SQL（OD 自连接派生）
                                 ▼
        ┌────────────────────────────────────────────┐
        │         Spring Boot（端口 8080）             │
        │  Controller → Service → FlowRepository      │
        │  5 个 REST 端点 + 静态资源托管               │
        └────────────────────────┬───────────────────┘
                                 │ HTTP /api/...
                                 ▼
        ┌────────────────────────────────────────────┐
        │      前端看板（原生 HTML/JS + ECharts 5）    │
        │  趋势折线 · OD 桑基/矩阵 · 人群画像三图       │
        └────────────────────────────────────────────┘
```

核心口径：所有指标（inflow/outflow/retained/population/画像）都从 OD 矩阵派生，保证三向对账一致。流动统计采用 transition-only 口径，只计入相邻窗口都在线的用户。

---

## 目录结构

```
ck_work/
├── flow-analysis/                  # 主模块：本项目的全部实现
│   ├── docker/
│   │   └── docker-compose.yml       # ClickHouse 24.3 单节点
│   ├── sql/
│   │   ├── init_ddl.sh              # 一键建表脚本
│   │   ├── ddl/                     # 5 张表的 DDL + 维度数据
│   │   └── analysis/                # 4 个分析 SQL（od/trend/portrait/regions）
│   ├── src/main/java/org/example/flow/
│   │   ├── controller/              # 5 个 REST Controller
│   │   ├── service/                 # 业务 Service
│   │   ├── repository/              # FlowRepository（JdbcTemplate）
│   │   ├── datagen/                 # DataGenerator 数据生成器
│   │   ├── model/                   # VO / 枚举 / ApiResp
│   │   ├── constant/                # SqlConst / RegionDef / ProfileConst
│   │   └── config/                  # 数据源 / CORS / 全局异常
│   ├── src/main/resources/
│   │   ├── application.yml          # 端口 8080、CH 连接、数据生成参数
│   │   └── static/                  # 前端看板（index.html + ECharts）
│   └── pom.xml
├── hbaseProcess/                   # 参考模块：原始 HBase 模拟数据来源（仅供参考，不参与运行）
├── docs/                           # 设计文档
└── README.md
```

`flow-analysis` 是需要运行的主模块。`hbaseProcess` 是数据口径的参考来源（区域/小区划分取自其中的 imitateDataSource.scala），实际运行不依赖它。

---

## 一键运行指南

### 前置依赖

| 工具 | 版本 | 说明 |
|---|---|---|
| Docker | 任意近期版本 | 跑 ClickHouse 容器 |
| JDK | 17（Amazon Corretto） | Spring Boot 2.7 不兼容过新的 JDK |
| Maven | 3.6+ | 打包 |

**关键：Maven 必须用 JDK 17 执行。** 本机默认 JDK 可能是更高版本，直接 `mvn` 会编译失败。每次跑 mvn 前先设好 `JAVA_HOME`：

```bash
# macOS
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 或直接指向 Corretto 17 路径
export JAVA_HOME=/Users/daiwenxi/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
```

下面的步骤都假设你在仓库根目录 `ck_work/` 下执行。

### 第 1 步：启动 ClickHouse

```bash
docker compose -f flow-analysis/docker/docker-compose.yml up -d
```

等容器通过 healthcheck（约 30 秒），确认能 ping 通：

```bash
curl http://localhost:8123/ping
# 期望输出：Ok.
```

容器名 `clickhouse-flow`，HTTP 端口 8123，native 端口 9000，库名 `flow`，用户 `default`，无密码。

### 第 2 步：建表

```bash
bash flow-analysis/sql/init_ddl.sh
```

脚本会按顺序建好 5 张表并灌入维度数据。最后打印 `All DDL executed successfully.` 即成功。

> 说明：ClickHouse HTTP 接口单次请求不支持多语句，含 TRUNCATE+INSERT 的维度脚本通过 `docker exec -i clickhouse-flow clickhouse-client --multiquery` 执行，脚本已自动处理。所有 DDL 用 `IF NOT EXISTS`，可安全重复运行。

### 第 3 步：打包

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests package -f flow-analysis/pom.xml
```

产物：`flow-analysis/target/flow-analysis-1.0.0-SNAPSHOT.jar`

### 第 4 步：运行应用

```bash
java -jar flow-analysis/target/flow-analysis-1.0.0-SNAPSHOT.jar
```

应用监听 8080 端口，并同源托管前端静态资源。

### 第 5 步：初始化数据（灌数）

应用启动后默认不自动灌数（`app.datagen.auto-init: false`）。手动触发一次：

```bash
curl -XPOST http://localhost:8080/api/admin/init-data
```

这一步会生成约 1800 万行 5 分钟明细，耗时约 1 到 3 分钟。完成后返回各表行数与 seed：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "seed": 20260601,
    "userCount": 10000,
    "profileRows": 10000,
    "dwdRows": 18144589,
    "dwsHourRows": 1680000,
    "dwsDayRows": 70000,
    "elapsedMs": 160000
  }
}
```

灌数是幂等的，重复调用会先 TRUNCATE 再重灌，结果可复现。

### 第 6 步：打开看板

浏览器访问：

```
http://localhost:8080/index.html
```

页面提供区域、粒度（小时/天）、方向（流入/流出）、画像维度、起止时间等控件，渲染趋势折线、OD 桑基图与矩阵、人群画像三图。

---

## API 一览

所有端点统一返回 `ApiResp{code, message, data}`。时间格式为 ISO `yyyy-MM-dd'T'HH:mm:ss`（时区 Asia/Shanghai）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regions` | 区域列表（含每区小区数） |
| GET | `/api/flow/trend` | 单区域流入/流出/留存/在网人数趋势 |
| GET | `/api/flow/od` | 区域间 OD 流向矩阵 |
| GET | `/api/flow/portrait` | 人群画像分布（性别/年龄段/常住） |
| POST | `/api/admin/init-data` | 触发数据生成（灌数，幂等） |

### 调用示例

```bash
# 1. 区域列表
curl "http://localhost:8080/api/regions"

# 2. 趋势：陆家嘴，小时粒度，2026-06-01 全天
curl "http://localhost:8080/api/flow/trend?regionId=310000_6254&granularity=hour&start=2026-06-01T00:00:00&end=2026-06-02T00:00:00"

# 3. OD 矩阵：小时粒度
curl "http://localhost:8080/api/flow/od?granularity=hour&start=2026-06-01T00:00:00&end=2026-06-02T00:00:00"

# 4. 画像：陆家嘴流入人群的性别分布
curl "http://localhost:8080/api/flow/portrait?regionId=310000_6254&granularity=hour&start=2026-06-01T00:00:00&end=2026-06-02T00:00:00&direction=in&dimension=gender"

# 5. 灌数
curl -XPOST "http://localhost:8080/api/admin/init-data"
```

参数约定：

- `granularity`：`hour` 或 `day`
- `direction`：`in`（流入）或 `out`（流出）
- `dimension`：`gender` / `age_group` / `is_resident`
- 非法枚举值或缺失必填参数返回 HTTP 400；不存在的区域返回 200 + 空数组。

3 个区域 ID：`310000_6254`（陆家嘴）、`310000_6234`（人民广场）、`310000_6200`（徐家汇）。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/01-需求分析.md](docs/01-需求分析.md) | 业务背景、分析目标、指标口径定义 |
| [docs/02-系统设计.md](docs/02-系统设计.md) | 总体架构、技术选型、数据库设计、分析算法、接口设计、前端、部署 |
| [docs/03-测试报告.md](docs/03-测试报告.md) | 测试环境与策略、功能用例、黄金数据集手算对照、守恒不变量、测试结果与证据 |
| [docs/04-项目总结.md](docs/04-项目总结.md) | 完成情况、关键技术难点与解决、局限性、收获心得、改进方向 |

---

## 技术栈

| 层 | 技术 |
|---|---|
| 存储与计算 | ClickHouse 24.3（Docker 单节点，库 flow，时区 Asia/Shanghai） |
| 后端 | JDK 17（Amazon Corretto） · Spring Boot 2.7.18 · JdbcTemplate |
| 数据库驱动 | clickhouse-jdbc 0.4.6（classifier=all） |
| 前端 | 原生 HTML / CSS / JavaScript · ECharts 5（本地引入，离线可用） |
| 构建 | Maven |

设计上刻意保持精简：无 Kafka、无 Flink、无 ORM 框架、无鉴权，分析逻辑全部落在 ClickHouse SQL 里，Java 层只做参数绑定与结果组装。

---

## 局限说明

- **数据为模拟生成**，非真实信令。用户轨迹由固定 seed 的随机模型产生，仅用于演示分析口径与可视化效果。
- **单节点 ClickHouse**，无副本、无分片，不代表生产部署形态。
- **离线分析**，非实时流处理。灌数为一次性批量任务，看板查询的是已落库的离线结果。
- **无鉴权与权限控制**，`/api/admin/init-data` 等接口对任何调用方开放，仅适合本地或受信网络。
- **OD 桑基图采用左右二部图**避免 3 区域互流成环，视觉上起点列与终点列分开展示，并非标准有向图布局。
- **OdController 的非法 granularity** 返回 HTTP 200 + body.code=400（其余端点为真 HTTP 400），属已知的小不一致，不影响数据正确性。
- `hbaseProcess` 模块仅作数据口径参考，未集成进运行链路。
