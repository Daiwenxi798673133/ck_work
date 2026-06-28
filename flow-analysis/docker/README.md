# ClickHouse 单节点 Docker 部署说明

## 配置信息

| 项目 | 值 |
|------|-----|
| 镜像 | `clickhouse/clickhouse-server:24.3` |
| 容器名 | `clickhouse-flow` |
| HTTP 端口 | `8123` |
| Native 端口 | `9000` |
| 数据库 | `flow` |
| 用户 | `default`（无密码） |
| 时区 | `Asia/Shanghai` |
| 数据卷 | `clickhouse_data`（命名卷持久化） |

## 启动

```bash
# 在项目根目录执行
docker compose -f flow-analysis/docker/docker-compose.yml up -d
```

首次启动会拉取镜像（约几百 MB），请耐心等待。

## 停止

```bash
docker compose -f flow-analysis/docker/docker-compose.yml down
```

> 数据卷不会被删除，重新 up 后数据依然存在。

## 彻底清除（含数据）

```bash
docker compose -f flow-analysis/docker/docker-compose.yml down -v
```

## 验证连接

```bash
# 检查服务是否就绪（期望输出：Ok.）
curl -s http://localhost:8123/ping

# 验证时区（期望输出：Asia/Shanghai）
curl -s "http://localhost:8123/?query=SELECT%20timezone()"

# 查看容器状态
docker ps | grep clickhouse
```

## 连接方式

### HTTP 接口
```bash
curl -s "http://localhost:8123/?query=SHOW+DATABASES"
```

### clickhouse-client（需安装客户端或进容器）
```bash
docker exec -it clickhouse-flow clickhouse-client --database=flow
```

### JDBC
```
URL:      jdbc:clickhouse://localhost:8123/flow
Driver:   com.clickhouse.jdbc.ClickHouseDriver
User:     default
Password: （空）
参数:     use_time_zone=Asia/Shanghai
```
