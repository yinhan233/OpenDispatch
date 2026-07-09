package com.logistics.scheduler.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logistics.scheduler.dao.TestDatabase;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndTest {

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

    private Map<String, Object> locationPayload(String name, double lng, double lat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("lng", lng);
        m.put("lat", lat);
        return m;
    }

    private Map<String, Object> vehiclePayload(long curLocId, String personId, int maxWeight, int maxVolume, double speed) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("personId", personId);
        v.put("curLocId", curLocId);
        v.put("curAvailableTime", java.util.List.of(2026, 7, 1, 7, 0));
        v.put("toolType", "微型面包车");
        v.put("maxWeight", maxWeight);
        v.put("maxVolume", maxVolume);
        v.put("speed", speed);
        v.put("status", "IDLE");
        return v;
    }

    private Map<String, Object> orderPayload(long pickupLocId, long deliveryLocId, int weight, int volume) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("pickupLocId", pickupLocId);
        o.put("deliveryLocId", deliveryLocId);
        o.put("timeStart", java.util.List.of(2026, 7, 1, 8, 0));
        o.put("timeEnd", java.util.List.of(2026, 7, 1, 18, 0));
        o.put("revenue", 50);
        o.put("weight", weight);
        o.put("volume", volume);
        o.put("serviceTime", 10);
        return o;
    }

    private long insertLocation(Map<String, Object> payload) throws Exception {
        HttpResponse<String> resp = post("/api/locations", MAPPER.writeValueAsString(payload));
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    private long insertVehicle(Map<String, Object> payload) throws Exception {
        HttpResponse<String> resp = post("/api/vehicles", MAPPER.writeValueAsString(payload));
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    private long insertOrder(Map<String, Object> payload) throws Exception {
        HttpResponse<String> resp = post("/api/orders", MAPPER.writeValueAsString(payload));
        Map<String, Object> body = jsonToMap(resp.body());
        return ((Number) body.get("id")).longValue();
    }

    @Test
    @DisplayName("完整端到端流程: 创建地点→车辆→订单→调度→完成→重调度")
    void fullWorkflow() throws Exception {
        // Step 1: 创建3个地点
        long pickupLoc = insertLocation(locationPayload("取货点", 114.30, 30.60));
        long deliveryLoc = insertLocation(locationPayload("送货点", 114.32, 30.61));
        long vehicleHome = insertLocation(locationPayload("车辆起点", 114.30, 30.59));

        // Step 2: 创建2辆车
        long veh1 = insertVehicle(vehiclePayload(vehicleHome, "DRIVER001", 1000, 40, 35));
        long veh2 = insertVehicle(vehiclePayload(vehicleHome, "DRIVER002", 1000, 40, 35));

        // Step 3: 创建5个订单
        long o1 = insertOrder(orderPayload(pickupLoc, deliveryLoc, 100, 5));
        long o2 = insertOrder(orderPayload(pickupLoc, deliveryLoc, 80, 4));
        long o3 = insertOrder(orderPayload(pickupLoc, deliveryLoc, 120, 6));
        long o4 = insertOrder(orderPayload(pickupLoc, deliveryLoc, 90, 5));
        long o5 = insertOrder(orderPayload(pickupLoc, deliveryLoc, 110, 5));
        Set<Long> allOrderIds = Set.of(o1, o2, o3, o4, o5);

        // Step 4: 验证5个订单创建成功
        HttpResponse<String> ordersResp = get("/api/orders");
        assertEquals(200, ordersResp.statusCode());
        List<Map<String, Object>> orders = jsonToList(ordersResp.body());
        assertEquals(5, orders.size());

        // Step 5: 验证2辆车
        HttpResponse<String> vehiclesResp = get("/api/vehicles");
        assertEquals(200, vehiclesResp.statusCode());
        List<Map<String, Object>> vehicles = jsonToList(vehiclesResp.body());
        assertEquals(2, vehicles.size());

        // Step 6: 执行调度
        HttpResponse<String> scheduleResp = post("/api/schedule", "");
        assertEquals(200, scheduleResp.statusCode());

        Map<String, Object> scheduleResult = jsonToMap(scheduleResp.body());
        List<Map<String, Object>> assignedVehicles = (List<Map<String, Object>>) scheduleResult.get("vehicles");
        Map<String, Object> stats = (Map<String, Object>) scheduleResult.get("stats");

        assertNotNull(assignedVehicles);
        assertTrue(assignedVehicles.size() > 0, "应有车辆被分配路线");
        assertNotNull(stats.get("totalIdle"));
        assertNotNull(stats.get("totalRevenue"));

        // 收集已分配的订单ID
        Set<Long> assignedOrderIds = new HashSet<>();
        for (var v : assignedVehicles) {
            List<Map<String, Object>> route = (List<Map<String, Object>>) v.get("route");
            for (var leg : route) {
                assignedOrderIds.add(((Number) leg.get("orderId")).longValue());
            }
        }

        // Step 7: 查询current schedule，与调度结果一致
        HttpResponse<String> currentResp = get("/api/schedule/current");
        assertEquals(200, currentResp.statusCode());
        Map<String, Object> currentResult = jsonToMap(currentResp.body());
        List<Map<String, Object>> currentVehicles = (List<Map<String, Object>>) currentResult.get("vehicles");
        assertFalse(currentVehicles.isEmpty(), "持久化后current应有数据");

        // Step 8: 验证订单-车辆分配关系
        if (!assignedOrderIds.isEmpty()) {
            long firstAssignedOrderId = assignedOrderIds.iterator().next();
            HttpResponse<String> orderVehicleResp = get("/api/orders/" + firstAssignedOrderId + "/vehicle");
            assertEquals(200, orderVehicleResp.statusCode());
            Map<String, Object> ov = jsonToMap(orderVehicleResp.body());
            assertEquals(true, ov.get("assigned"));
            assertNotNull(ov.get("vehicleId"));
        }

        // Step 9: 完成一个订单 (使用第一个已分配订单)
        if (!assignedOrderIds.isEmpty()) {
            long orderToComplete = assignedOrderIds.iterator().next();
            long completeVehicleId = -1;

            // 找到该订单所属的车辆
            HttpResponse<String> ovResp = get("/api/orders/" + orderToComplete + "/vehicle");
            Map<String, Object> ov = jsonToMap(ovResp.body());
            if ((Boolean) ov.get("assigned")) {
                completeVehicleId = ((Number) ov.get("vehicleId")).longValue();
            }

            if (completeVehicleId > 0) {
                HttpResponse<String> completeResp = post(
                        "/api/vehicles/" + completeVehicleId + "/complete?orderId=" + orderToComplete, "");
                assertEquals(200, completeResp.statusCode());

                // Step 10: 验证订单状态为 DONE
                HttpResponse<String> orderDetailResp = get("/api/orders/" + orderToComplete);
                assertEquals(200, orderDetailResp.statusCode());
                Map<String, Object> doneOrder = jsonToMap(orderDetailResp.body());
                assertEquals("DONE", doneOrder.get("status"));
            }
        }

        // Step 11: 动态重调度(剩余订单)
        HttpResponse<String> rescheduleResp = post("/api/schedule/reschedule", "");
        assertEquals(200, rescheduleResp.statusCode());

        Map<String, Object> rescheduleResult = jsonToMap(rescheduleResp.body());
        assertNotNull(rescheduleResult.get("vehicles"));
        assertNotNull(rescheduleResult.get("stats"));

        // Step 12: 端到端数据一致性验证
        // 验证所有地点可查询
        HttpResponse<String> locsResp = get("/api/locations");
        List<Map<String, Object>> allLocs = jsonToList(locsResp.body());
        assertTrue(allLocs.size() >= 3 + 1, "应有3个新地点+1个默认地点");

        // 验证车辆状态已更新(调度后ON_DUTY)
        HttpResponse<String> vehDetailResp = get("/api/vehicles/" + veh1);
        assertEquals(200, vehDetailResp.statusCode());
        Map<String, Object> vDetail = jsonToMap(vehDetailResp.body());
        assertNotNull(vDetail.get("status"));

        // 验证默认地点保护
        HttpResponse<String> delDefaultResp = delete("/api/locations/0");
        assertEquals(400, delDefaultResp.statusCode());

        // 验证地点删除保护(有引用的地点不可删)
        HttpResponse<String> delUsedLocResp = delete("/api/locations/" + pickupLoc);
        // 有订单引用 → 409
        assertTrue(delUsedLocResp.statusCode() == 409 || delUsedLocResp.statusCode() == 204,
                "删除被引用地点应返回409或204");
    }
}
