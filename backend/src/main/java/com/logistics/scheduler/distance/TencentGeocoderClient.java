package com.logistics.scheduler.distance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.scheduler.config.MapKeyConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 腾讯位置服务 - 地址解析(geocoder/v1)客户端,支持 SN 签名校验.
 *
 * Key/SK 从 MapKeyConfig 读取(~/.logistics_manager/mapkey.json),支持热更新.
 */
public class TencentGeocoderClient {

    private static final String ENDPOINT = "https://apis.map.qq.com/ws/geocoder/v1";
    private static final String PATH = "/ws/geocoder/v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MapKeyConfig keyConfig;
    private final HttpClient http;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 3600_000L;

    private record CacheEntry(double[] coords, long timestamp) {}

    /** 兼容旧调用: 从系统属性/环境变量构造. */
    public TencentGeocoderClient() {
        this(new MapKeyConfig());
    }

    /** 兼容旧调用: 直接指定 key/sk(会写入 MapKeyConfig). */
    public TencentGeocoderClient(String key, String sk) {
        this.keyConfig = new MapKeyConfig();
        this.keyConfig.save(key, sk);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public TencentGeocoderClient(MapKeyConfig keyConfig) {
        this.keyConfig = keyConfig;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 地址 -> 经纬度. 成功返回 [lng, lat], 失败返回 null.
     * 带1小时内存缓存,避免重复消耗配额.
     */
    public double[] geocode(String address) {
        String key = keyConfig.getKey();
        String sk = keyConfig.getSk();
        if (key == null || key.isBlank()) {
            System.err.println("[TencentGeocoder] key未配置,请在前端[设置]中输入Key/SK,或编辑 " + keyConfig.getConfigPath());
            return null;
        }
        // 1. 查缓存
        CacheEntry cached = cache.get(address);
        if (cached != null && System.currentTimeMillis() - cached.timestamp() < CACHE_TTL_MS) {
            return cached.coords();
        }
        // 2. 实际调用
        int n = callCount.incrementAndGet();
        System.out.println("[TencentGeocoder] call #" + n + " for: " + address);
        double[] result = doGeocode(address, key, sk);
        // 3. 成功则缓存
        if (result != null) {
            cache.put(address, new CacheEntry(result, System.currentTimeMillis()));
        }
        return result;
    }

    /** 实际调用腾讯接口. */
    private double[] doGeocode(String address, String key, String sk) {
        try {
            Map<String, String> rawParams = new TreeMap<>();
            rawParams.put("key", key);
            rawParams.put("address", address);
            StringBuilder sigBase = new StringBuilder(PATH).append("?");
            StringBuilder query = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> e : rawParams.entrySet()) {
                if (!first) { sigBase.append("&"); query.append("&"); }
                first = false;
                sigBase.append(e.getKey()).append("=").append(e.getValue());
                query.append(e.getKey()).append("=")
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
            String sig = md5Hex(sigBase.toString() + sk);
            String url = ENDPOINT + "?" + query + "&sig=" + sig;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = MAPPER.readTree(resp.body());
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                String msg = root.path("message").asText("unknown error");
                System.err.println("[TencentGeocoder] geocode failed (status=" + status + "): " + msg
                        + " | address=" + address + " | body=" + resp.body());
                return null;
            }
            JsonNode loc = root.path("result").path("location");
            double lng = loc.path("lng").asDouble();
            double lat = loc.path("lat").asDouble();
            return new double[]{lng, lat};
        } catch (Exception e) {
            System.err.println("[TencentGeocoder] error: " + e.getMessage());
            return null;
        }
    }

    /** 清除缓存(更换 key 后调用). */
    public void clearCache() {
        cache.clear();
    }

    /** 计算 MD5(小写十六进制). */
    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
