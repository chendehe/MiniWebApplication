package com.chendehe.etcd;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import com.coreos.jetcd.Watch;
import com.coreos.jetcd.Watch.Watcher;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.data.KeyValue;
import com.coreos.jetcd.kv.GetResponse;
import com.coreos.jetcd.options.DeleteOption;
import com.coreos.jetcd.options.GetOption;
import com.coreos.jetcd.options.WatchOption;
import com.coreos.jetcd.watch.WatchEvent;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigService.class);
  private static final String SEPARATOR = ",";

  private static Client client;

  public static void init(EtcdConfig config) {
    if (null != client) {
      return;
    }
    synchronized (ConfigService.class) {
      client = Client.builder().endpoints(config.getUrl().split(SEPARATOR)).build();
    }
  }

  private static void destroy() {
    if (null != client) {
      //client.close();
    }
  }

  public static void put(String key, String val) {
    try (KV kvClient = client.getKVClient()) {
      kvClient.put(ByteSequence.fromString(key), ByteSequence.fromString(val)).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      destroy();
    }
  }

  public static Map<String, String> get(String key) {
    return get(key, GetOption.DEFAULT);
  }

  public static Map<String, String> getPrefix(String key) {
    return get(key, GetOption.newBuilder().withPrefix(ByteSequence.fromString(key)).build());
  }

  public static Map<String, String> get(String key, GetOption option) {

    List<KeyValue> kvs = null;
    try (KV kvClient = client.getKVClient()) {
      CompletableFuture<GetResponse> future = kvClient.get(ByteSequence.fromString(key), option);
      kvs = future.get().getKvs();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      destroy();
    }

    if (CollectionUtils.isEmpty(kvs)) {
      return null;
    }

    Map<String, String> map = new HashMap<>(kvs.size());
    for (KeyValue kv : kvs) {
      map.put(kv.getKey().toStringUtf8(), kv.getValue().toStringUtf8());
    }
    return map;
  }

  public static void delete(String key) {
    delete(key, DeleteOption.DEFAULT);
  }

  public static void delete(String key, DeleteOption option) {

    try (KV kvClient = client.getKVClient()) {
      kvClient.delete(ByteSequence.fromString(key), option).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      destroy();
    }
  }

  public static Map<String, String> watch(String key) {
    return watch(key, WatchOption.DEFAULT);
  }

  public static Map<String, String> watchPrefix(String key) {
    return watch(key, WatchOption.newBuilder().withPrefix(ByteSequence.fromString(key)).build());
  }

  public static Map<String, String> watch(String key, WatchOption option) {

    List<WatchEvent> events = null;
    Watch watch = client.getWatchClient();
    try (Watcher watcher = watch.watch(ByteSequence.fromString(key), option)) {
      events = watcher.listen().getEvents();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (CollectionUtils.isEmpty(events)) {
      return null;
    }

    Map<String, String> map = new HashMap<>(events.size());
    for (WatchEvent event : events) {
      String eventKey = event.getKeyValue().getKey().toStringUtf8();
      LOGGER.info("{} key: {}", event.getEventType(), eventKey);
      map.put(eventKey, event.getKeyValue().getValue().toStringUtf8());
    }
    for (Entry<String, ChangeConfigListener> entry : listeners.entrySet()) {
      if (events.get(0).getKeyValue().getKey().toStringUtf8().startsWith(entry.getKey())) {
        entry.getValue().receiveConfig(map);
        return map;
      }
    }
    return map;
  }

  private static final ConcurrentHashMap<String, ChangeConfigListener> listeners = new ConcurrentHashMap<>();

  /**
   * 添加事件监听
   * @param preKey 不能互为前缀
   * @param listener 监听
   */
  public static void addListener(final String preKey, ChangeConfigListener listener) {
    LOGGER.info("listen...{}", preKey);
    Preconditions.checkNotNull(preKey, "preKey can't be null");
    new Thread(() -> {
      listeners.put(preKey, listener);
      for (; ; ) {
        watchPrefix(preKey);
      }
    }).start();
  }
}
