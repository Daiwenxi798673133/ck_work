package org.example.dataProcess

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Admin, Connection, Table}
import org.example.utils.{BasicUtil, HbaseUtil, MD5Util}

/**
 * @author: YinZiran
 * @description:模拟数据源
 * @date:2024/5/27 19:57
 * @updateHistory：
 *
 */
object imitateDataSource {


  def main(args: Array[String]): Unit = {

    //获取连接
    val connection: Connection = HbaseUtil.getConnection()
    val admin: Admin = connection.getAdmin

    //模拟上游读kafka数据实时写入hbase-5分钟基站人群明细【TO_MI_EVNT_NS_CELL_IMSI】
    val tableName: TableName = TableName.valueOf("WZMF:TO_MI_EVNT_NS_CELL_IMSI")
    val table: Table = connection.getTable(tableName)

    //列族名
    val familyName = "crowds"
    //310000是上海市的行政区编码,我们以上海市某个区域关联到的基站小区作为数据样例
    //以基站小区编号为列名，假设整个上海只有8个基站小区，如下
    val qualifierNames: Array[String] = Array("310000_6254_140854179", "310000_6254_140854180", "310000_6254_140854181",
      "310000_6234_140854182", "310000_6234_140854183", "310000_6200_140854184", "310000_6200_140854185", "310000_6200_140854186")

    //循环插入26年6月1日至2日的共24*12个5min切片的基站明细数据，1个小时有12个5min切片数据
    // 设置起始时间和结束时间
    val startDateTime: LocalDateTime = LocalDateTime.of(2026, 6, 1, 0, 0)
    val endDateTime: LocalDateTime = LocalDateTime.of(2026, 6, 2, 0, 0)

    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    // 以5分钟为间隔遍历时间范围
    var currentDateTime: LocalDateTime = startDateTime
    while (currentDateTime.isBefore(endDateTime)) {
      println(currentDateTime)
      for (qualifierName <- qualifierNames) {
        //rowKey设计成MD5{基站标识}{0,4}+基站标识+时间
        val rowKey: String = MD5Util.md5(qualifierName).take(4) + "_" + currentDateTime.format(formatter)
        //qualifierName列名使用基站标识
        //对应列名的value值为imsi字符串(使用md5加密)使用逗号分隔，此处使用随机数模拟
        val value: String = BasicUtil.generateRandomHexes
        HbaseUtil.insertData(table, familyName, qualifierName, rowKey, value)
      }
      currentDateTime = currentDateTime.plusMinutes(5)
    }

    // 关闭资源
    table.close()
    admin.close()
    connection.close()
  }

}

