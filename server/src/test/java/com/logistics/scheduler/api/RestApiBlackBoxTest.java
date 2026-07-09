package com.logistics.scheduler.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logistics.scheduler.dao.TestDatabase;
import com.logistics.scheduler.model.Order;
import com.logistics.scheduler.model.Vehicle;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RestApiBlackBoxTest {

    private static Javalin app;
    private static HttpClient client;
    private static int port;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeAll
    static void start() {
        app = TestAppHelper.create(TestDatabase.getDataSource());
        app.start(0);
        port = app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        app.stop();
    }

    @BeforeEach
    void clean() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception {
        return post(path, MAPPER.writeValueAsString(body));
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .DELETE().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private List<Map<String, Object>> jsonToList(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    private Map<String, Object> jsonToMap(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    private Map<String, Object> createLocationMap(String name, double lng, double lat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("lng", lng);
        m.put("lat", lat);
        return m;
    }

    private Map<String, Object> createOrderPayload(long pickupLocId, long deliveryLocId) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("pickupLocId", pickupLocId);
        o.put("deliveryLocId", deliveryLocId);
        o.put("timeStart", java.util.List.of(2026, 7, 1, 8, 0));
        o.put("timeEnd", java.util.List.of(2026, 7, 1, 18, 0));
        o.put("revenue", 50);
        o.put("weight", 100);
        o.put("volume", 5);
        o.put("serviceTime", 10);
        return o;
    }

    private Map<String, Object> createVehiclePayload(long curLocId, int maxWeight, int maxVolume, double speed) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("personId", "TEST001");
        v.put("curLocId", curLocId);
        v.put("curAvailableTime", java.util.List.of(2026, 7, 1, 7, 0));
        v.put("toolType", "微型面包车");
        v.put("maxWeight", maxWeight);
        v.put("maxVolume", maxVolume);
        v.put("speed", speed);
        v.put("status", "IDLE");
        return v;
    }

    private long insertLocation(String name, double lng, double lat) throws Exception {
        HttpResponse<String> resp = postJson("/api/locations", createLocationMap(name, lng, lat));
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    private long insertVehicle(Map<String, Object> payload) throws Exception {
        HttpResponse<String> resp = postJson("/api/vehicles", payload);
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    private long insertOrder(Map<String, Object> payload) throws Exception {
        HttpResponse<String> resp = postJson("/api/orders", payload);
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    // ======================== Order APIs ========================

    @Test
    @DisplayName("GET /api/orders 边界值:空数据 返回[]")
    void getAllOrders_Empty() throws Exception {
        HttpResponse<String> resp = get("/api/orders");
        assertEquals(200, resp.statusCode());
        List<Map<String, Object>> list = jsonToList(resp.body());
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("GET /api/orders 等价类:非空 返回2条")
    void getAllOrders_WithOrders() throws Exception {
        insertOrder(createOrderPayload(0L, 0L));
        insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = get("/api/orders");
        assertEquals(200, resp.statusCode());
        List<Map<String, Object>> list = jsonToList(resp.body());
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("GET /api/orders/status/{status} 等价类:按状态过滤")
    void getOrdersByStatus() throws Exception {
        insertOrder(createOrderPayload(0L, 0L));
        insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = get("/api/orders/status/UNASSIGNED");
        assertEquals(200, resp.statusCode());
        List<Map<String, Object>> list = jsonToList(resp.body());
        assertEquals(2, list.size());
        for (var o : list) assertEquals("UNASSIGNED", o.get("status"));
    }

    @Test
    @DisplayName("GET /api/orders/status/{status} 边界值:无匹配返回空")
    void getOrdersByStatus_NoMatch() throws Exception {
        insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = get("/api/orders/status/DONE");
        assertEquals(200, resp.statusCode());
        List<Map<String, Object>> list = jsonToList(resp.body());
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("POST /api/orders 等价类:有效输入 → 200")
    void createOrder_Success() throws Exception {
        Map<String, Object> payload = createOrderPayload(0L, 0L);
        HttpResponse<String> resp = postJson("/api/orders", payload);
        assertEquals(200, resp.statusCode());

        Map<String, Object> o = jsonToMap(resp.body());
        assertNotNull(o.get("id"));
        assertEquals("UNASSIGNED", o.get("status"));
    }

    @Test
    @DisplayName("POST /api/orders 等价类:无效输入-空body → 500")
    void createOrder_MissingRequired() throws Exception {
        HttpResponse<String> resp = post("/api/orders", "{}");
        // 缺少 timeStart/timeEnd 等必填字段,序列化时 Timestamp.valueOf(null) 将抛 NPE
        assertEquals(500, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/orders 等价类:无效输入-负收益 → 200(DB不校验)")
    void createOrder_NegativeRevenue() throws Exception {
        Map<String, Object> payload = createOrderPayload(0L, 0L);
        payload.put("revenue", -100);
        HttpResponse<String> resp = postJson("/api/orders", payload);
        assertEquals(200, resp.statusCode());

        Map<String, Object> o = jsonToMap(resp.body());
        assertEquals("UNASSIGNED", o.get("status"));
    }

    @Test
    @DisplayName("POST /api/orders 边界值:timeStart=timeEnd → 200")
    void createOrder_EmptyTimeWindow() throws Exception {
        Map<String, Object> payload = createOrderPayload(0L, 0L);
        payload.put("timeEnd", java.util.List.of(2026, 7, 1, 8, 0));
        HttpResponse<String> resp = postJson("/api/orders", payload);
        assertEquals(200, resp.statusCode());

        Map<String, Object> o = jsonToMap(resp.body());
        assertNotNull(o.get("id"));
    }

    @Test
    @DisplayName("POST /api/orders/batch 等价类:批量正常 → 3条")
    void createOrderBatch() throws Exception {
        List<Map<String, Object>> batch = new ArrayList<>();
        batch.add(createOrderPayload(0L, 0L));
        batch.add(createOrderPayload(0L, 0L));
        batch.add(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = postJson("/api/orders/batch", batch);
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> result = jsonToList(resp.body());
        assertEquals(3, result.size());
        for (var o : result) {
            assertNotNull(o.get("id"));
            assertEquals("UNASSIGNED", o.get("status"));
        }
    }

    @Test
    @DisplayName("GET /api/orders/{id} 等价类:有效ID → 200")
    void getOrder_Success() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = get("/api/orders/" + id);
        assertEquals(200, resp.statusCode());

        Map<String, Object> o = jsonToMap(resp.body());
        assertEquals(id, ((Number) o.get("id")).longValue());
    }

    @Test
    @DisplayName("GET /api/orders/{id} 等价类:无效ID → 404")
    void getOrder_NotFound() throws Exception {
        HttpResponse<String> resp = get("/api/orders/9999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/orders/{id}/status 等价类:有效状态 → 200")
    void setOrderStatus_Valid() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = post("/api/orders/" + id + "/status", "{\"status\":\"EXECUTING\"}");
        assertEquals(200, resp.statusCode());

        Map<String, Object> o = jsonToMap(resp.body());
        assertEquals("EXECUTING", o.get("status"));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/status 等价类:无效状态 → 400")
    void setOrderStatus_Invalid() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = post("/api/orders/" + id + "/status", "{\"status\":\"INVALID\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/orders/{id}/status 等价类:缺少status字段 → 400")
    void setOrderStatus_MissingBody() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = post("/api/orders/" + id + "/status", "{}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("GET /api/orders/{id}/vehicle 等价类:未分配 → assigned=false")
    void getOrderVehicle_Unassigned() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = get("/api/orders/" + id + "/vehicle");
        assertEquals(200, resp.statusCode());

        Map<String, Object> m = jsonToMap(resp.body());
        assertEquals(false, m.get("assigned"));
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} 等价类:正常删除 → 204")
    void deleteOrder_Success() throws Exception {
        long id = insertOrder(createOrderPayload(0L, 0L));

        HttpResponse<String> resp = delete("/api/orders/" + id);
        assertEquals(204, resp.statusCode());

        HttpResponse<String> getResp = get("/api/orders/" + id);
        assertEquals(404, getResp.statusCode());
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} 等价类:无效ID删除 → 204(SQL删除0行)")
    void deleteOrder_NotFound() throws Exception {
        HttpResponse<String> resp = delete("/api/orders/9999");
        assertEquals(204, resp.statusCode());
    }

    // ======================== Vehicle APIs ========================

    @Test
    @DisplayName("GET /api/vehicles/types 语句覆盖 → 8种预设")
    void getVehicleTypes() throws Exception {
        HttpResponse<String> resp = get("/api/vehicles/types");
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> types = jsonToList(resp.body());
        assertEquals(8, types.size());
        assertTrue(types.stream().anyMatch(t -> "微型面包车".equals(t.get("name"))));
        assertTrue(types.stream().anyMatch(t -> "电瓶车".equals(t.get("name"))));
        assertTrue(types.stream().anyMatch(t -> "大型重卡".equals(t.get("name"))));
    }

    @Test
    @DisplayName("GET /api/vehicles 边界值:空数据 → []")
    void getAllVehicles_Empty() throws Exception {
        HttpResponse<String> resp = get("/api/vehicles");
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> list = jsonToList(resp.body());
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("POST /api/vehicles 等价类:有效输入 → 200")
    void createVehicle_Success() throws Exception {
        Map<String, Object> payload = createVehiclePayload(0L, 500, 4, 35);
        HttpResponse<String> resp = postJson("/api/vehicles", payload);
        assertEquals(200, resp.statusCode());

        Map<String, Object> v = jsonToMap(resp.body());
        assertNotNull(v.get("id"));
        assertEquals("IDLE", v.get("status"));
    }

    @Test
    @DisplayName("POST /api/vehicles 等价类:默认值 → 自动填充personId/curLocId")
    void createVehicle_DefaultFields() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolType", "微型面包车");
        payload.put("curAvailableTime", java.util.List.of(2026, 7, 1, 7, 0));
        payload.put("maxWeight", 500);
        payload.put("maxVolume", 4);
        payload.put("speed", 35);

        HttpResponse<String> resp = postJson("/api/vehicles", payload);
        assertEquals(200, resp.statusCode());

        Map<String, Object> v = jsonToMap(resp.body());
        assertNotNull(v.get("id"));
        assertNotNull(v.get("personId"));
        assertFalse(((String) v.get("personId")).isBlank());
    }

    @Test
    @DisplayName("POST /api/vehicles/batch 等价类:批量 → 3个创建")
    void createVehicleBatch() throws Exception {
        List<Map<String, Object>> batch = new ArrayList<>();
        batch.add(createVehiclePayload(0L, 500, 4, 35));
        batch.add(createVehiclePayload(0L, 1500, 12, 40));
        batch.add(createVehiclePayload(0L, 3000, 20, 45));

        HttpResponse<String> resp = postJson("/api/vehicles/batch", batch);
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> result = jsonToList(resp.body());
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("GET /api/vehicles/{id} 等价类:无效ID → 404")
    void getVehicle_NotFound() throws Exception {
        HttpResponse<String> resp = get("/api/vehicles/9999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/vehicles/{id}/status 等价类:无效状态 → 400")
    void setVehicleStatus_Invalid() throws Exception {
        long id = insertVehicle(createVehiclePayload(0L, 500, 4, 35));

        HttpResponse<String> resp = post("/api/vehicles/" + id + "/status", "{\"status\":\"INVALID\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/vehicles/{id}/offline 等价类:下线操作 → OFFLINE")
    void offlineVehicle() throws Exception {
        long id = insertVehicle(createVehiclePayload(0L, 500, 4, 35));

        HttpResponse<String> resp = post("/api/vehicles/" + id + "/offline", "");
        assertEquals(200, resp.statusCode());

        Map<String, Object> v = jsonToMap(resp.body());
        assertEquals("OFFLINE", v.get("status"));
    }

    @Test
    @DisplayName("DELETE /api/vehicles/{id} 等价类:删除 → 204")
    void deleteVehicle() throws Exception {
        long id = insertVehicle(createVehiclePayload(0L, 500, 4, 35));

        HttpResponse<String> resp = delete("/api/vehicles/" + id);
        assertEquals(204, resp.statusCode());

        HttpResponse<String> getResp = get("/api/vehicles/" + id);
        assertEquals(404, getResp.statusCode());
    }

    // ======================== Location APIs ========================

    @Test
    @DisplayName("GET /api/locations 语句覆盖 → 至少包含ID=0默认地点")
    void getAllLocations_IncludesDefault() throws Exception {
        HttpResponse<String> resp = get("/api/locations");
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> list = jsonToList(resp.body());
        assertFalse(list.isEmpty());
        boolean hasDefault = list.stream().anyMatch(l -> ((Number) l.get("id")).longValue() == 0L);
        assertTrue(hasDefault, "应包含默认地点(ID=0)");
    }

    @Test
    @DisplayName("POST /api/locations 等价类:有效 → 200")
    void createLocation_Success() throws Exception {
        HttpResponse<String> resp = postJson("/api/locations", createLocationMap("测试地点", 114.30, 30.59));
        assertEquals(200, resp.statusCode());

        Map<String, Object> loc = jsonToMap(resp.body());
        assertEquals("测试地点", loc.get("name"));
        assertNotNull(loc.get("id"));
    }

    @Test
    @DisplayName("POST /api/locations 边界值:极端坐标 → 200")
    void createLocation_NegativeCoords() throws Exception {
        HttpResponse<String> resp = postJson("/api/locations", createLocationMap("极端坐标", -200.0, 100.0));
        assertEquals(200, resp.statusCode());

        Map<String, Object> loc = jsonToMap(resp.body());
        assertEquals(-200.0, ((Number) loc.get("lng")).doubleValue(), 0.001);
        assertEquals(100.0, ((Number) loc.get("lat")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("POST /api/locations/geocode 等价类:空地址 → 400")
    void geocode_EmptyAddress() throws Exception {
        HttpResponse<String> resp = post("/api/locations/geocode", "{\"address\":\"\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/locations/geocode 等价类:过短地址(<3字符) → 400")
    void geocode_ShortAddress() throws Exception {
        HttpResponse<String> resp = post("/api/locations/geocode", "{\"address\":\"ab\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("DELETE /api/locations/{id} 等价类:保护默认地点ID=0 → 400")
    void deleteDefaultLocation() throws Exception {
        HttpResponse<String> resp = delete("/api/locations/0");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("DELETE /api/locations/{id} 等价类:正常删除非默认地点 → 204")
    void deleteLocation_Success() throws Exception {
        long id = insertLocation("待删除地点", 114.30, 30.59);

        HttpResponse<String> resp = delete("/api/locations/" + id);
        assertEquals(204, resp.statusCode());

        HttpResponse<String> getResp = get("/api/locations/" + id);
        assertEquals(404, getResp.statusCode());
    }

    // ======================== Schedule APIs ========================

    @Test
    @DisplayName("POST /api/schedule 边界值:空数据 → empty result, feasible=true")
    void schedule_EmptyData() throws Exception {
        HttpResponse<String> resp = post("/api/schedule", "");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        List<Map<String, Object>> unassigned = (List<Map<String, Object>>) result.get("unassigned");
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");

        assertTrue(vehicles.isEmpty());
        assertTrue(unassigned.isEmpty());
        assertEquals(true, stats.get("feasible"));
    }

    @Test
    @DisplayName("POST /api/schedule 等价类:仅有订单无车辆 → 全部unassigned")
    void schedule_OnlyOrders() throws Exception {
        insertLocation("取货点", 114.30, 30.60);
        insertLocation("送货点", 114.32, 30.61);
        insertOrder(createOrderPayload(1L, 2L));
        insertOrder(createOrderPayload(1L, 2L));

        HttpResponse<String> resp = post("/api/schedule", "");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        List<Integer> unassigned = (List<Integer>) result.get("unassigned");
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");

        assertTrue(vehicles.isEmpty());
        assertEquals(2, unassigned.size());
        assertEquals(false, stats.get("feasible"));
    }

    @Test
    @DisplayName("POST /api/schedule 等价类:正常调度 → vehicles assigned")
    void schedule_Normal() throws Exception {
        insertLocation("取货点A", 114.30, 30.60);
        insertLocation("送货点A", 114.32, 30.61);
        insertLocation("车辆起点", 114.30, 30.59);

        long v1 = insertVehicle(createVehiclePayload(3L, 1000, 40, 35));
        long v2 = insertVehicle(createVehiclePayload(3L, 1000, 40, 35));

        insertOrder(createOrderPayload(1L, 2L));
        insertOrder(createOrderPayload(1L, 2L));

        HttpResponse<String> resp = post("/api/schedule", "");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");

        assertNotNull(vehicles);
        assertTrue(vehicles.size() > 0, "应有车辆被分配");
        assertNotNull(stats.get("totalIdle"));
        assertNotNull(stats.get("totalRevenue"));
    }

    @Test
    @DisplayName("GET /api/schedule/dry-run 黑盒:验证不落库")
    void dryRun_NoPersist() throws Exception {
        insertLocation("取货点A", 114.30, 30.60);
        insertLocation("送货点A", 114.32, 30.61);
        insertLocation("车辆起点", 114.30, 30.59);
        insertVehicle(createVehiclePayload(3L, 1000, 40, 35));
        insertOrder(createOrderPayload(1L, 2L));

        HttpResponse<String> dryRun = get("/api/schedule/dry-run");
        assertEquals(200, dryRun.statusCode());

        HttpResponse<String> current = get("/api/schedule/current");
        Map<String, Object> cur = jsonToMap(current.body());
        List<Map<String, Object>> curVehicles = (List<Map<String, Object>>) cur.get("vehicles");
        assertTrue(curVehicles.isEmpty(), "dry-run不应持久化路线");
    }

    @Test
    @DisplayName("GET /api/schedule/current 边界值:空调度 → empty")
    void currentSchedule_Empty() throws Exception {
        HttpResponse<String> resp = get("/api/schedule/current");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        assertTrue(vehicles.isEmpty());
    }

    @Test
    @DisplayName("GET /api/schedule/current 等价类:持久化后查询 → 返回数据")
    void currentSchedule_AfterSchedule() throws Exception {
        insertLocation("取货点A", 114.30, 30.60);
        insertLocation("送货点A", 114.32, 30.61);
        insertLocation("车辆起点", 114.30, 30.59);
        insertVehicle(createVehiclePayload(3L, 1000, 40, 35));
        insertOrder(createOrderPayload(1L, 2L));

        post("/api/schedule", "");

        HttpResponse<String> resp = get("/api/schedule/current");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        assertFalse(vehicles.isEmpty(), "持久化后 current 应有路线数据");
    }

    @Test
    @DisplayName("GET /api/schedule/debug/arcs 语句覆盖 → 返回弧列表")
    void debugArcs() throws Exception {
        insertLocation("取货点", 114.30, 30.60);
        insertLocation("送货点", 114.32, 30.61);
        insertLocation("车辆起点", 114.30, 30.59);
        insertVehicle(createVehiclePayload(3L, 1000, 40, 35));
        insertOrder(createOrderPayload(1L, 2L));

        HttpResponse<String> resp = get("/api/schedule/debug/arcs");
        assertEquals(200, resp.statusCode());

        List<Map<String, Object>> arcs = jsonToList(resp.body());
        assertNotNull(arcs);
        // 只要有数据,至少有几个弧(源→车, 源→订单, 车→订单, 订单→汇...)
        assertTrue(arcs.size() > 0, "应有弧列表");
    }

    @Test
    @DisplayName("POST /api/schedule/reschedule 边界值:空数据 → handles gracefully")
    void reschedule_Empty() throws Exception {
        HttpResponse<String> resp = post("/api/schedule/reschedule", "");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        assertTrue(vehicles.isEmpty());
    }

    @Test
    @DisplayName("GET /api/schedule/dry-run-dynamic 边界值:空数据 → handles empty")
    void dryRunDynamic_Empty() throws Exception {
        HttpResponse<String> resp = get("/api/schedule/dry-run-dynamic");
        assertEquals(200, resp.statusCode());

        Map<String, Object> result = jsonToMap(resp.body());
        assertNotNull(result);
    }

    // ======================== Config APIs ========================

    @Test
    @DisplayName("GET /api/config/mapkey 等价类:配置接口可访问")
    void getMapKeyConfig() throws Exception {
        HttpResponse<String> resp = get("/api/config/mapkey");
        assertEquals(200, resp.statusCode());

        Map<String, Object> config = jsonToMap(resp.body());
        assertNotNull(config.get("configured"));
        assertNotNull(config.get("path"));
    }

    @Test
    @DisplayName("POST /api/config/mapkey 等价类:缺少必填字段 → 400")
    void saveMapKeyConfig_MissingKey() throws Exception {
        HttpResponse<String> resp = post("/api/config/mapkey", "{\"sk\":\"testsk\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("POST /api/config/mapkey 等价类:有效key → ok=true")
    void saveMapKeyConfig_WithKey() throws Exception {
        HttpResponse<String> resp = post("/api/config/mapkey", "{\"key\":\"test123\"}");
        assertEquals(200, resp.statusCode());

        Map<String, Object> m = jsonToMap(resp.body());
        assertEquals(true, m.get("ok"));
        assertEquals(true, m.get("configured"));
    }
}
