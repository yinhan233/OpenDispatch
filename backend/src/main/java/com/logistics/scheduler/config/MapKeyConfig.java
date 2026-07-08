package com.logistics.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯地图 Key/SK 本地配置文件管理.
 *
 * 文件路径: ~/.logistics_manager/mapkey.json
 * 格式:
 * {
 *   "key": "XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX",
 *   "sk":  "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
 * }
 *
 * 启动时优先从文件读取; 文件不存在则用环境变量/系统属性; 都没有则 key 为空(geocode 返回 null).
 * 前端可通过 POST /api/config/mapkey 保存新 key/sk, 会实时写入文件并热更新 GeocoderClient.
 */
public class MapKeyConfig {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".logistics_manager");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("mapkey.json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private volatile String key = "";
    private volatile String sk = "";

    public MapKeyConfig() {
        load();
    }

    /** 从文件加载; 文件不存在则尝试环境变量/系统属性. */
    public void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                @SuppressWarnings("unchecked")
                Map<String, String> m = MAPPER.readValue(CONFIG_FILE.toFile(), Map.class);
                key = m.getOrDefault("key", "");
                sk = m.getOrDefault("sk", "");
            } else {
                // fallback: 环境变量 / 系统属性
                key = System.getProperty("tencent.map.key",
                        System.getenv().getOrDefault("TENCENT_MAP_KEY", ""));
                sk = System.getProperty("tencent.map.sk",
                        System.getenv().getOrDefault("TENCENT_MAP_SK", ""));
            }
        } catch (IOException e) {
            System.err.println("[MapKeyConfig] 读取配置失败: " + e.getMessage());
        }
    }

    /** 保存 key/sk 到文件. */
    public synchronized void save(String key, String sk) {
        try {
            Files.createDirectories(CONFIG_DIR);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", key != null ? key : "");
            m.put("sk", sk != null ? sk : "");
            MAPPER.writeValue(CONFIG_FILE.toFile(), m);
            this.key = key != null ? key : "";
            this.sk = sk != null ? sk : "";
            System.out.println("[MapKeyConfig] 已保存到 " + CONFIG_FILE);
        } catch (IOException e) {
            throw new RuntimeException("保存地图Key配置失败: " + e.getMessage(), e);
        }
    }

    public String getKey() { return key; }
    public String getSk() { return sk; }
    public boolean isConfigured() { return key != null && !key.isBlank(); }
    public Path getConfigPath() { return CONFIG_FILE; }
}
