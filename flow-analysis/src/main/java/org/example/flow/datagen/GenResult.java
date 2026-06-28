package org.example.flow.datagen;

/**
 * 数据生成结果 VO。
 *
 * <p>由 {@link DataGenerator#generate()} 返回，承载本次生成的关键统计指标，
 * 供后续 T12 的 {@code POST /api/admin/init-data} 接口直接复用为响应体。
 *
 * @param seed         本次生成使用的随机种子（可复现性凭据）
 * @param userCount    生成的用户数
 * @param profileRows  写入 flow.dim_user_profile 的行数（应等于 userCount）
 * @param dwdRows      写入 flow.dwd_cell_imsi_5min 的明细行数
 * @param dwsHourRows  flow.dws_user_window_loc 中 granularity='hour' 的行数
 * @param dwsDayRows   flow.dws_user_window_loc 中 granularity='day' 的行数
 * @param elapsedMs    总耗时（毫秒）
 */
public record GenResult(
        long seed,
        int userCount,
        long profileRows,
        long dwdRows,
        long dwsHourRows,
        long dwsDayRows,
        long elapsedMs
) {
}
