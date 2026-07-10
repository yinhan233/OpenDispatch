package com.logistics.scheduler.api;

import com.logistics.scheduler.config.AppConfig;
import com.logistics.scheduler.config.MapKeyConfig;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/** 地图key */
public class ConfigApi {
    private final MapKeyConfig mapKeyConfig;

    public ConfigApi(AppConfig cfg) {
        this.mapKeyConfig = cfg.mapKeyConfig;
    }

    public void getMapKey(Context ctx) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", mapKeyConfig.getKey() != null && !mapKeyConfig.getKey().isBlank()
                ? maskKey(mapKeyConfig.getKey()) : "");
        m.put("sk", mapKeyConfig.getSk() != null && !mapKeyConfig.getSk().isBlank() ? "******" : "");
        m.put("configured", mapKeyConfig.isConfigured());
        m.put("path", mapKeyConfig.getConfigPath().toString());
        ctx.json(m);
    }

    public void saveMapKey(Context ctx) throws Exception {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String key = (String) body.get("key");
        String sk = (String) body.get("sk");
        if (key == null || key.isBlank()) {
            ctx.status(400).result("key is required");
            return;
        }
        mapKeyConfig.save(key.trim(), sk != null ? sk.trim() : "");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("configured", mapKeyConfig.isConfigured());
        resp.put("path", mapKeyConfig.getConfigPath().toString());
        ctx.json(resp);
    }
    private String maskKey(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
