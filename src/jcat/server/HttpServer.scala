package jcat.server

import java.io.File
import java.net.ServerSocket
import scala.actors.threadpool.Executors
import java.net.Socket
import com.sun.org.apache.xml.internal.utils.StringToIntTable
import java.util.StringTokenizer
import org.omg.IOP.Encoding
import java.io.BufferedOutputStream
import java.util.Date
import scala.util.Random
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.nio.charset.Charset
import java.util.Date
import java.util.StringTokenizer
import scala.collection.mutable.HashMap
import scala.util.Random
import java.net.SocketOptions
import java.net.URLDecoder

case class JsonMsg(kind: String, short: String, long: String)

object HttpServer {
  val encoding = "UTF-8";
  val webRoot = new File(".");
  val defaultFile = "index.html";
  val lineSep = File.separator;

  val db = HashMap[Int, String]()
  val BASE_TIME = System.currentTimeMillis()
  case class Status(code: Int, text: String);

  def main(args: Array[String]) {

    val port = 8888;
    val serverSocket = new ServerSocket(port);
    println("Lisening for connections on port " + port);

    val pool = Executors.newCachedThreadPool();
    while (true)
      pool.execute(new HttpServer(serverSocket.accept()));
  }
}

object Util {
  val BASE = 62;
  val UPPERCASE_OFFSET = 55
  val LOWERCASE_OFFSET = 61
  val DIGIT_OFFSET = 48

  def dehydrate(number: Int): String = {
    var tempNum = number

    val strBuilder = new StringBuffer;
    while (tempNum > 0) {
      val remainder = tempNum % BASE;
      strBuilder.append(numToChar(remainder))
      tempNum = tempNum / BASE;
    }
    strBuilder.toString()
  }

  def saturate(short: String): Int = {
    var sum: Int = 0
    val chars = short.toCharArray()
    List.range(0, chars.length).foreach(i => sum += (charToNum(chars(i)) * (Math.pow(BASE, i)).toInt))
    sum
  }

  def numToChar(num: Int): Char = {
    if (num < 10)
      (num + DIGIT_OFFSET).toChar
    else if (10 <= num && num <= 35)
      (num + UPPERCASE_OFFSET).toChar
    else if (36 <= num && num < 62)
      (num + LOWERCASE_OFFSET).toChar
    else
      '#'
  }

  def charToNum(char: Char): Int = {
    if (Character.isDigit(char))
      char.toInt - DIGIT_OFFSET
    else if ('A' <= char && char <= 'Z')
      char.toInt - UPPERCASE_OFFSET
    else if ('a' <= char && char <= 'z')
      char.toInt - LOWERCASE_OFFSET
    else
      0
  }
}

class HttpServer(socket: Socket) extends Runnable() {
  import HttpServer._;
  val rng = new Random();

  import HttpServer.db
  import HttpServer.BASE_TIME

  val longUrl = """/url\?longUrl=(.*)""".r
  val shortUrl = """/url\?shortUrl=.*/([\w\d]+)""".r

  def respond(status: Status, contentType: String = "text/html", content: Array[Byte]) {
    val out = new BufferedOutputStream(socket.getOutputStream())

    val header = s"""
      |HTTP/1.1 ${status.code} ${status.text}
      |Server: Scala HTTP Server 1.0
      |Date: ${new Date()}
      |Content-type: application/json;charset=UTF-8
      |Content-length: ${content.length}
      |
      |${content}
    """.trim.stripMargin
    header.getBytes("UTF-8")
    try {
      out.write(header.getBytes(encoding))
      out.flush()

      out.write(content)
      out.flush()
    } finally {
      out.close()
    }
  }

  def respondWithError(status: Status, contentType: String = "text/html") {
    val out = new BufferedOutputStream(socket.getOutputStream())

    val content = s"""{"error": true,"code": 404,"message": "Not found"}"""

    val header = s"""
      |HTTP/1.1 ${status.code} ${status.text}
      |Server: Scala HTTP Server 1.0
      |Date: ${new Date()}
      |Content-type: application/json;charset=UTF-8
      |Content-length: ${content.length}
      |
      |${content}
    """.trim.stripMargin
    header.getBytes("UTF-8")
    try {
      out.write(header.getBytes(encoding))
      out.flush()
    } finally {
      out.close()
    }
  }

  def respondWithJson(status: Status, contentType: String = "application/json", body: JsonMsg) {
    val out = new BufferedOutputStream(socket.getOutputStream())

    val content = s"""
					|{"kind": "${body.kind}","shortUrl": "http://127.0.0.1:8888/${body.short}","longUrl": "${body.long}"}
			""".trim().stripMargin

    val header = s"""
      |HTTP/1.1 ${status.code} ${status.text}
      |Server: Scala HTTP Server 1.0
      |Date: ${new Date()}
      |Content-type: ${contentType};charset=UTF-8
      |Content-length: ${content.length}
      |
      |${content}
    """.trim.stripMargin
    header.getBytes("UTF-8")
    try {
      out.write(header.getBytes(encoding))
      out.flush()
    } finally {
      out.close()
    }
  }

  def run() {
    println("Connection opened.")
    val source = io.Source.fromInputStream(socket.getInputStream(), "UTF-8");

    val line = source.getLines.next;
    val tokens = new StringTokenizer(line);
    println("Line::::" + line);

    tokens.nextToken().toUpperCase() match {
      case method @ ("GET" | "HEAD") =>
        var path = tokens.nextToken
        println("Path on nextToken:::::" + path);
        path match {
          case url @ ("/") =>
            respondWithError(Status(200, "OK"))
          case url @ longUrl(a) =>
            val key: Int = (System.currentTimeMillis() - BASE_TIME).toInt + rng.nextInt(10000)
            val str = Util.dehydrate(key)
            println("Long url coming with ::::" + key + " " + a);
            db.put(key, a)
            respondWithJson(Status(200, "OK"), "application/json", new JsonMsg("shorten", str, a))
          case url @ shortUrl(a) =>
            val long = db.get(Util.saturate(a)).get
            respondWithJson(Status(200, "OK"), "application/json", new JsonMsg("expand", a, long))
            println("Short url coming with ::::" + long);

          case _ =>
            respondWithError(Status(200, "OK"))
        }
        println("Get handled")
      //Thread.sleep(100);
      //        respondWithHtml(
      //          Status(200, "OK"),
      //          title = "200 OK",
      //          body = <H2>200 OK: { method } method</H2>)
      case method =>
        print("yyyyyyy")
      //        respondWithHtml(
      //          Status(501, "Not Implemented"),
      //          title = "501 Not Implemented",
      //          body = <H2>501 Not Implemented: { method } method</H2>)
    }
  }
}