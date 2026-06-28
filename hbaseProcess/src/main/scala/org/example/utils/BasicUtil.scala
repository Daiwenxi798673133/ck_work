package org.example.utils

import scala.util.Random
/**
 * @author: YinZiran
 * @description:
 * @date:2024/5/28 21:25
 * @updateHistory：
 *
 */
object BasicUtil {
  val hexChars = "0123456789abcdef"

  // 生成单个随机的32个字符长度的十六进制字符串
  def randomHex: String = {
    (1 to 32).map(_ => hexChars(Random.nextInt(16))).mkString
  }

  // 生成10个随机的十六进制字符串，并用逗号连接
  def generateRandomHexes: String = {
    (1 to 10).map(_ => randomHex).mkString(",")
  }
}
