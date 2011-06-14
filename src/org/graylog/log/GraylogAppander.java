package org.graylog.log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.codehaus.jackson.map.ObjectMapper;

public class GraylogAppander extends AppenderSkeleton {
  private static final Logger log = Logger.getLogger(GraylogAppander.class);

  private static final int SHORT_MESSAGE_LENGTH = 250;
  private static final String GELF_VERSION = "1.0";
  private static final String ID = "id";
  private static final String VERSION = "version";
  private static final String LEVEL = "level";
  private static final String HOST = "host";
  private static final String FULL_MESSAGE = "full_message";
  private static final String SHORT_MESSAGE = "short_message";
  private static final String TIMESTAMP = "timestamp";
  private static final String FACILITY = "facility";
  private static final String FILE = "file";
  private static final String LINE = "line";

  private GraylogClient graylogClient;
  private int graylogPort;
  private String graylogHost;
  private String host;
  private final String facility = "unknown";
  private Map<String, String> fields;

  @Override
  public void activateOptions() {
    graylogClient = new GraylogClient(graylogHost, graylogPort);
  }

  @Override
  protected void append(LoggingEvent event) {
    try {
      Map<String, Object> map = createMessageMap(event);
      if (map != null) {
        graylogClient.sendMessage(new ObjectMapper().writeValueAsString(map));
      }
    } catch (Exception ex) {
      log.warn("Failed to log " + event, ex);
    }
  }

  Map<String, Object> createMessageMap(LoggingEvent loggingEvent) {
    String fullMessage = loggingEvent.getRenderedMessage();
    String shortMessage = fullMessage.length() > SHORT_MESSAGE_LENGTH ? fullMessage.substring(0,
        SHORT_MESSAGE_LENGTH - 1) : fullMessage;

    if (isEmpty(shortMessage) || isEmpty(fullMessage) || isEmpty(host)) {
      return null;
    }

    Map<String, Object> map = new HashMap<String, Object>();
    map.put(VERSION, GELF_VERSION);
    map.put(LEVEL, loggingEvent.getLevel().getSyslogEquivalent());
    map.put(HOST, getHost());
    map.put(FULL_MESSAGE, fullMessage);
    map.put(SHORT_MESSAGE, shortMessage);
    map.put(TIMESTAMP, System.currentTimeMillis());
    map.put(FACILITY, facility);
    map.put(FILE, loggingEvent.getLocationInformation().getFileName());
    map.put(LINE, loggingEvent.getLocationInformation().getLineNumber());

    if (fields != null) {
      for (String field : fields.keySet()) {
        if (!ID.equals(field)) {
          map.put("_" + field, fields.get(field));
        }
      }
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  public void setAdditionalFields(String additionalFields) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      fields = mapper.readValue(additionalFields, HashMap.class);
    } catch (Exception e) {
      log.error("failed to read additional fields", e);
    }
  }

  public int getGraylogPort() {
    return graylogPort;
  }

  public void setGraylogPort(int graylogPort) {
    this.graylogPort = graylogPort;
  }

  public String getGraylogHost() {
    return graylogHost;
  }

  public void setGraylogHost(String graylogHost) {
    this.graylogHost = graylogHost;
  }

  public String getHost() {
    if (host == null) {
      try {
        host = InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException e) {
      }
    }
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

  @Override
  public void close() {
  }

  private boolean isEmpty(String text) {
    return text == null || text.trim().length() == 0;
  }
}
