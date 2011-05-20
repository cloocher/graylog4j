package org.greylog.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

public class GraylogClient {
  private static final Logger logger = Logger.getLogger(GraylogClient.class);

  private static final int DEFAULT_PORT = 12201;
  public static final int MAX_CHUNK_SIZE = 1420;
  public static final byte[] HEADER_PREFIX = new byte[] { 0x1e, 0x0f };

  private DatagramSocket socket;
  private InetAddress host;
  private int port;

  public GraylogClient(String host, int port) {
    try {
      this.host = InetAddress.getByName(host);
      this.port = port > 0 ? port : DEFAULT_PORT;
      this.socket = new DatagramSocket();
    } catch (Exception e) {
      logger.error("failed to connect to Graylog server", e);
    }
  }

  public void sendMessage(String message) {
    byte[] bytes = toGzippedBytes(message);
    if (bytes.length > MAX_CHUNK_SIZE) {
      byte[] messageId = UUID.randomUUID().toString().replaceAll("-", "").getBytes();
      int total = (int) Math.ceil((double) bytes.length / MAX_CHUNK_SIZE);
      for (int i = 0; i < total; i++) {
        byte[] headerPostfix = new byte[] { 0x00, (byte) i, 0x00, (byte) total };
        int start = i * MAX_CHUNK_SIZE;
        int end = Math.min(start + MAX_CHUNK_SIZE, bytes.length);
        byte[] result = new byte[HEADER_PREFIX.length + messageId.length + headerPostfix.length + end - start];
        int offset = 0;
        byte[][] bytesArrays = new byte[][] { HEADER_PREFIX, messageId, headerPostfix };
        for (byte[] byteArray : bytesArrays) {
          System.arraycopy(byteArray, 0, result, offset, byteArray.length);
          offset += byteArray.length;
        }
        System.arraycopy(bytes, start, result, offset, end - start);
        sendBytes(result);
      }
    } else {
      sendBytes(bytes);
    }
  }

  private void sendBytes(byte[] bytes) {
    try {
      socket.send(new DatagramPacket(bytes, bytes.length, host, port));
    } catch (IOException e) {
      logger.error("failed to send message", e);
    }
  }

  public void close() {
    if (socket != null && socket.isConnected()) {
      socket.close();
    }
  }

  private byte[] toGzippedBytes(String message) {
    byte[] bytes = null;
    ByteArrayOutputStream baos = null;
    GZIPOutputStream gos = null;
    try {
      baos = new ByteArrayOutputStream();
      gos = new GZIPOutputStream(baos);
      gos.write(message.getBytes());
      closeOutputStream(gos);
      bytes = baos.toByteArray();
      closeOutputStream(baos);
    } catch (IOException e) {
      logger.error("failed to gzip message", e);
    }
    return bytes;
  }

  private void closeOutputStream(OutputStream os) {
    if (os != null) {
      try {
        os.close();
      } catch (IOException e) {
      }
    }
  }
}
