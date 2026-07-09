#include <QtTest>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include "ApiClient.h"
#include "Models.h"

class TestApiClient : public QObject {
    Q_OBJECT

private slots:
    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 构造与销毁不崩溃
    void testConstructor() {
        ApiClient *client = new ApiClient(this);
        QVERIFY(client != nullptr);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 带自定义baseUrl的构造函数
    void testConstructorWithBaseUrl() {
        ApiClient *client = new ApiClient("http://127.0.0.1:9090", this);
        QVERIFY(client != nullptr);
        QCOMPARE(client->baseUrl(), "http://127.0.0.1:9090");
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 非WASM构建默认baseUrl
    void testDefaultBaseUrl() {
        ApiClient *client = new ApiClient(this);
#ifndef __EMSCRIPTEN__
        QCOMPARE(client->baseUrl(), "http://localhost:8080");
#else
        QCOMPARE(client->baseUrl(), "");
#endif
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — setBaseUrl改变baseUrl值
    void testSetBaseUrl() {
        ApiClient *client = new ApiClient(this);
        client->setBaseUrl("https://api.example.com");
        QCOMPARE(client->baseUrl(), "https://api.example.com");
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — getOrders不崩溃（无后端时回调不会被调用）
    void testGetOrdersSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->getOrders([&](QList<QJsonObject>) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true); // 若无后端，回调不会触发；仅验证不崩溃
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — createOrder不崩溃
    void testCreateOrderSmoke() {
        ApiClient *client = new ApiClient(this);
        QJsonObject order;
        order["status"] = "UNASSIGNED";
        bool called = false;
        client->createOrder(order, [&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — deleteOrder不崩溃
    void testDeleteOrderSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->deleteOrder(1, [&]() {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — getVehicles不崩溃
    void testGetVehiclesSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->getVehicles([&](QList<QJsonObject>) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — getVehicleTypes不崩溃
    void testGetVehicleTypesSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->getVehicleTypes([&](QList<QJsonObject>) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — createVehicle不崩溃
    void testCreateVehicleSmoke() {
        ApiClient *client = new ApiClient(this);
        QJsonObject vehicle;
        vehicle["toolType"] = "微型面包车";
        bool called = false;
        client->createVehicle(vehicle, [&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — getLocations不崩溃
    void testGetLocationsSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->getLocations([&](QList<QJsonObject>) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — createLocation不崩溃
    void testCreateLocationSmoke() {
        ApiClient *client = new ApiClient(this);
        QJsonObject loc;
        loc["name"] = "Test";
        loc["lng"] = 114.30;
        loc["lat"] = 30.59;
        bool called = false;
        client->createLocation(loc, [&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — triggerSchedule不崩溃
    void testTriggerScheduleSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->triggerSchedule([&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — dryRunSchedule不崩溃
    void testDryRunScheduleSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->dryRunSchedule([&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — reschedule不崩溃
    void testRescheduleSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->reschedule([&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — currentSchedule不崩溃
    void testCurrentScheduleSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->currentSchedule([&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — completeOrder不崩溃
    void testCompleteOrderSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->completeOrder(1, 100, [&]() {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — geocode不崩溃
    void testGeocodeSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->geocode("武汉市", [&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — saveMapKeyConfig不崩溃
    void testSaveMapKeyConfigSmoke() {
        ApiClient *client = new ApiClient(this);
        bool called = false;
        client->saveMapKeyConfig("key123", "sk456", [&](QJsonObject) {
            called = true;
        });
        QTest::qWait(100);
        QVERIFY(true);
        delete client;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 执行完所有API后析构不崩溃（资源清理验证）
    void testApiClientLifecycle() {
        ApiClient *client = new ApiClient(this);
        client->getOrders([](QList<QJsonObject>) {});
        client->getVehicles([](QList<QJsonObject>) {});
        client->getLocations([](QList<QJsonObject>) {});
        client->getVehicleTypes([](QList<QJsonObject>) {});
        client->triggerSchedule([](QJsonObject) {});
        client->dryRunSchedule([](QJsonObject) {});
        client->currentSchedule([](QJsonObject) {});
        client->reschedule([](QJsonObject) {});
        QTest::qWait(100);
        delete client;
        QVERIFY(true);
    }
};

QTEST_MAIN(TestApiClient)
#include "test_apiclient.moc"
