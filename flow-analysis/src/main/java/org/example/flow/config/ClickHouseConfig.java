package org.example.flow.config;

import org.springframework.context.annotation.Configuration;

/**
 * ClickHouse 数据源配置说明。
 *
 * <p>Spring Boot 2.7 + spring-boot-starter-jdbc 会根据 application.yml 中的
 * spring.datasource.* 配置自动装配 {@link javax.sql.DataSource}（HikariCP）
 * 以及 {@link org.springframework.jdbc.core.JdbcTemplate}，无需手动声明 Bean。
 *
 * <pre>
 * # application.yml 关键配置：
 * spring:
 *   datasource:
 *     url: jdbc:clickhouse://localhost:8123/flow?use_time_zone=Asia/Shanghai
 *     driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
 *     username: default
 *     password:          # 无密码
 * </pre>
 *
 * <p>如需定制连接池参数（最大连接数等），在 application.yml 的
 * {@code spring.datasource.hikari.*} 节点配置即可；本类不额外声明 Bean，
 * 保持最小侵入原则。
 */
@Configuration
public class ClickHouseConfig {
}
