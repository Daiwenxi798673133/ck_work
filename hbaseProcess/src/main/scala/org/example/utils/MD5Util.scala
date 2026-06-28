package org.example.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author: YinZiran
 * @description:
 * @date:2024/5/28 20:48
 * @updateHistory：
 *
 */
object MD5Util {
  // MD5加密的方法
  def md5(s: String): String = {
    // 创建MessageDigest实例，指定MD5算法
    val instance: MessageDigest = MessageDigest.getInstance("MD5")

    // 对字符串进行MD5加密
    val digest: Array[Byte] = instance.digest(s.getBytes)

    // 将加密后的字节数组转换为十六进制字符串
    digest.map("%02x".format(_)).mkString
  }

}
