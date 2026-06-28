package org.example.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{CellUtil, HBaseConfiguration, HColumnDescriptor, HTableDescriptor, KeyValue, NamespaceDescriptor, TableName}
import org.apache.hadoop.hbase.client.{Admin, Connection, ConnectionFactory, Get, Put, Result, Table}
import org.apache.hadoop.hbase.util.Bytes

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * @author: YinZiran
 * @description:建表及查表测试，需注意创建表无法成功，一直运行的原因是没有权限访问
 * @date:2024/6/3 22:15
 * @updateHistory：
 *
 */
object HbaseUtil {

  def getConnection(): Connection = {
    val conf: Configuration = HBaseConfiguration.create()
    conf.set("hbase.zookeeper.quorum", "bigdata01")
    conf.set("hbase.zookeeper.property.clientPort", "2181")

    conf.set("hbase.rootdir", "hdfs://bigdata01:9000/hbase")

    val connection: Connection = ConnectionFactory.createConnection(conf)
    println("连接hbase成功")
    connection
  }

  // 创建表
  def createTable(connection: Connection, tbName: String, familyName: String): Unit = {
    val admin: Admin = connection.getAdmin
    val tableName: TableName = TableName.valueOf(tbName)
    if (!admin.tableExists(tableName)) {
      val desc = new HTableDescriptor(tableName)
      val colFamily = new HColumnDescriptor(familyName)
      desc.addFamily(colFamily)
      admin.createTable(desc)
      println(s"$tbName 建表成功")
    }
  }

  //打印所有命名空间
  def printAllNameSpace(connection: Connection): Unit = {
    try {
      // 获取Admin对象
      val admin: Admin = connection.getAdmin

      // 获取所有命名空间
      val namespaces: Array[NamespaceDescriptor] = admin.listNamespaceDescriptors()

      // 打印所有命名空间名称
      namespaces.foreach { nsDesc =>
        println(nsDesc.getName)
      }
    } catch {
      case e: Exception => println("Error occurred while trying to list tables: " + e.getMessage)
        e.printStackTrace()
    }
    finally {
      // 关闭连接
      if (connection != null) connection.close()
    }
  }

  // 获取所有表名
  def getAllTableNames(connection: Connection, namespace: String): Unit = {
    println(s"命名空间为 $namespace")
    try {
      // 获取Admin对象
      val admin: Admin = connection.getAdmin

      // 检查HBase表是否可用
      if (admin.isTableAvailable(TableName.NAMESPACE_TABLE_NAME)) {
        // 获取特定命名空间的所有表名
        val tables: Array[TableName] = admin.listTableNamesByNamespace(namespace)
        // 打印表名
        tables.foreach {
          table =>
            println(table.getNameAsString)
        }
      } else {
        println("命名空间表不可用")
      }
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      // 关闭连接
      if (connection != null) connection.close()
    }
  }

  //查看应用层区域人流表的数据
  def getSomeRegionData(connection: Connection): Unit = {
    val tableNameR: TableName = TableName.valueOf("WZMF:TA_H_REGION_CROWD_FLOW")
    val tableR: Table = connection.getTable(tableNameR)
    val regionId = "255027778846457856"
    val testTime = "2024052821"
    val testRowKey: String = MD5Util.md5(regionId).take(4) + "_" + regionId + "_" + testTime
    val get = new Get(Bytes.toBytes(testRowKey))
    get.addFamily(Bytes.toBytes("crowds"))
    val res: Result = tableR.get(get)
    if (res != null && !res.isEmpty) {
      val cellsIterable: mutable.Seq[KeyValue] = res.list().asScala
      cellsIterable.foreach { cell =>
        // 获取列
        val qualifier: String = Bytes.toString(CellUtil.cloneQualifier(cell))
        // 获取值
        val value: String = Bytes.toString(CellUtil.cloneValue(cell))
        println(s"列: $qualifier,值: $value")
      }
    }
  }

  //查看明细层基站人员明细表的数据
  def getSomeCellData(connection: Connection): Unit = {
    val tableNameR: TableName = TableName.valueOf("WZMF:TO_MI_EVNT_NS_CELL_IMSI")
    val tableR: Table = connection.getTable(tableNameR)
    val laccell = "310000_6254_140854179"
    val testTime = "202405280000"
    val testRowKey: String = MD5Util.md5(laccell).take(4) + "_" + testTime
    val get = new Get(Bytes.toBytes(testRowKey))
    get.addFamily(Bytes.toBytes("crowds"))
    val res: Result = tableR.get(get)
    if (res != null && !res.isEmpty) {
      val cellsIterable: mutable.Seq[KeyValue] = res.list().asScala
      cellsIterable.foreach { cell =>
        // 获取列
        val qualifier: String = Bytes.toString(CellUtil.cloneQualifier(cell))
        // 获取值
        val value: String = Bytes.toString(CellUtil.cloneValue(cell))
        println(s"列: $qualifier,值: $value")
      }
    }
  }

  // 插入数据
  def insertData(table: Table, familyName: String, qualifierName: String, rowKey: String, value: String): Unit = {
    val put = new Put(rowKey.getBytes)
    put.addColumn(familyName.getBytes, qualifierName.getBytes, value.getBytes)
    table.put(put)
  }

  def main(args: Array[String]): Unit = {
    //获取连接
    val connection: Connection = getConnection()

    //测试查区域人流数据,可以查成功
//    getSomeRegionData(connection)
    //测试查基站明细数据
    //getSomeCellData(connection)

    //测试插入数据,也插入成功
    val tableNameR: TableName = TableName.valueOf("WZMF:TO_MI_EVNT_NS_CELL_IMSI")
    val tableR: Table = connection.getTable(tableNameR)
    val testRegionId = "337920201708273664"
    val testTime = "2024060301"
    val testRowKey: String = MD5Util.md5(testRegionId).take(4) + "_" + testRegionId + "_" + testTime
    insertData(tableR, "crowds", "keep_num", testRowKey, "100")

    //打印所有命名空间,无法成功
    //printAllNameSpace(connection)

    //打印某个命名空间下的所有表,无法成功
    //getAllTableNames(connection, "WZMF")

    //创建新表无法成功
//    val tbName = "WZMF:TO_MI_EVNT_NS_CELL_IMSI"
//    val familyName = "crowds"
//    createTable(connection, tbName, familyName)
  }
}
