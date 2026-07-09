#include <QtTest>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include "Models.h"

class TestModels : public QObject {
    Q_OBJECT

private slots:
    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 正常JSON
    void testLocationFromJson() {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(42));
        o["name"] = "武汉仓库";
        o["lng"] = 114.30;
        o["lat"] = 30.59;

        LocationData d = LocationData::fromJson(o);
        QCOMPARE(d.id, Q_UINT64_C(42));
        QCOMPARE(d.name, "武汉仓库");
        QCOMPARE(d.lng, 114.30);
        QCOMPARE(d.lat, 30.59);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — toJson输出与预期字段一致
    void testLocationToJson() {
        LocationData d;
        d.id = 99;
        d.name = "北京中心";
        d.lng = 116.40;
        d.lat = 39.90;

        QJsonObject o = d.toJson();
        QCOMPARE(o["id"].toVariant().toULongLong(), Q_UINT64_C(99));
        QCOMPARE(o["name"].toString(), "北京中心");
        QCOMPARE(o["lng"].toDouble(), 116.40);
        QCOMPARE(o["lat"].toDouble(), 39.90);
    }

    // 测试类型: 白盒测试
    // 测试方法: 数据完整性 — toJson → fromJson 往返
    void testLocationRoundtrip() {
        LocationData orig;
        orig.id = 7;
        orig.name = "测试点";
        orig.lng = 121.47;
        orig.lat = 31.23;

        QJsonObject o = orig.toJson();
        LocationData restored = LocationData::fromJson(o);
        QCOMPARE(restored.id, Q_UINT64_C(7));
        QCOMPARE(restored.name, "测试点");
        QCOMPARE(restored.lng, 121.47);
        QCOMPARE(restored.lat, 31.23);
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 空JSON使用默认值
    void testLocationEmptyJson() {
        QJsonObject o;
        LocationData d = LocationData::fromJson(o);
        QCOMPARE(d.id, Q_UINT64_C(0));
        QVERIFY(d.name.isEmpty());
        QCOMPARE(d.lng, 0.0);
        QCOMPARE(d.lat, 0.0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 部分字段JSON，其余使用默认值
    void testLocationPartialJson() {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(5));
        o["lng"] = 113.26;

        LocationData d = LocationData::fromJson(o);
        QCOMPARE(d.id, Q_UINT64_C(5));
        QCOMPARE(d.lng, 113.26);
        QVERIFY(d.name.isEmpty());
        QCOMPARE(d.lat, 0.0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 完整OrderData JSON，含QDateTime
    void testOrderFromJson() {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(1001));
        o["taskId"] = QJsonValue::fromVariant(QVariant(2001));
        o["pickupLocId"] = QJsonValue::fromVariant(QVariant(10));
        o["deliveryLocId"] = QJsonValue::fromVariant(QVariant(20));
        o["timeStart"] = "2025-06-01T08:00:00";
        o["timeEnd"] = "2025-06-01T10:00:00";
        o["revenue"] = 150.5;
        o["weight"] = 200.0;
        o["volume"] = 3.5;
        o["serviceTime"] = 30;
        o["status"] = "ASSIGNED";
        o["createdAt"] = "2025-05-30T12:00:00";

        OrderData d = OrderData::fromJson(o);
        QCOMPARE(d.id, Q_UINT64_C(1001));
        QCOMPARE(d.taskId, Q_UINT64_C(2001));
        QCOMPARE(d.pickupLocId, Q_UINT64_C(10));
        QCOMPARE(d.deliveryLocId, Q_UINT64_C(20));
        QVERIFY(d.timeStart.isValid());
        QCOMPARE(d.timeStart, QDateTime(QDate(2025, 6, 1), QTime(8, 0, 0)));
        QVERIFY(d.timeEnd.isValid());
        QCOMPARE(d.timeEnd, QDateTime(QDate(2025, 6, 1), QTime(10, 0, 0)));
        QCOMPARE(d.revenue, 150.5);
        QCOMPARE(d.weight, 200.0);
        QCOMPARE(d.volume, 3.5);
        QCOMPARE(d.serviceTime, 30);
        QCOMPARE(d.status, "ASSIGNED");
        QVERIFY(d.createdAt.isValid());
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — toJson输出所有字段
    void testOrderToJson() {
        OrderData d;
        d.id = 500;
        d.taskId = 600;
        d.pickupLocId = 1;
        d.deliveryLocId = 2;
        d.timeStart = QDateTime(QDate(2025, 7, 1), QTime(9, 0, 0));
        d.timeEnd = QDateTime(QDate(2025, 7, 1), QTime(11, 0, 0));
        d.revenue = 99.9;
        d.weight = 50.0;
        d.volume = 1.2;
        d.serviceTime = 20;
        d.status = "UNASSIGNED";

        QJsonObject o = d.toJson();
        QCOMPARE(o["id"].toVariant().toULongLong(), Q_UINT64_C(500));
        QCOMPARE(o["taskId"].toVariant().toULongLong(), Q_UINT64_C(600));
        QCOMPARE(o["revenue"].toDouble(), 99.9);
        QCOMPARE(o["weight"].toDouble(), 50.0);
        QCOMPARE(o["volume"].toDouble(), 1.2);
        QCOMPARE(o["serviceTime"].toInt(), 20);
        QCOMPARE(o["status"].toString(), "UNASSIGNED");
        QVERIFY(!o["timeStart"].toString().isEmpty());
        QVERIFY(!o["timeEnd"].toString().isEmpty());
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 默认OrderData的status为"UNASSIGNED"
    void testOrderDefaultStatus() {
        OrderData d;
        QCOMPARE(d.status, "UNASSIGNED");

        QJsonObject o = d.toJson();
        QCOMPARE(o["status"].toString(), "UNASSIGNED");
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — VehicleData含shiftStart/shiftEnd
    void testVehicleFromJson() {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(10));
        o["personId"] = "DRV001";
        o["status"] = "BUSY";
        o["curLocId"] = QJsonValue::fromVariant(QVariant(3));
        o["toolType"] = "微型面包车";
        o["maxWeight"] = 800.0;
        o["maxVolume"] = 6.0;
        o["speed"] = 40.0;
        o["shiftStart"] = "2025-07-01T06:00:00";
        o["shiftEnd"] = "2025-07-01T18:00:00";
        o["earnedRevenue"] = 320.0;

        VehicleData d = VehicleData::fromJson(o);
        QCOMPARE(d.id, Q_UINT64_C(10));
        QCOMPARE(d.personId, "DRV001");
        QCOMPARE(d.status, "BUSY");
        QCOMPARE(d.curLocId, Q_UINT64_C(3));
        QCOMPARE(d.toolType, "微型面包车");
        QCOMPARE(d.maxWeight, 800.0);
        QCOMPARE(d.maxVolume, 6.0);
        QCOMPARE(d.speed, 40.0);
        QVERIFY(d.shiftStart.isValid());
        QCOMPARE(d.shiftStart, QDateTime(QDate(2025, 7, 1), QTime(6, 0, 0)));
        QVERIFY(d.shiftEnd.isValid());
        QCOMPARE(d.shiftEnd, QDateTime(QDate(2025, 7, 1), QTime(18, 0, 0)));
        QCOMPARE(d.earnedRevenue, 320.0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 无shift时间时QDateTime.toString()返回空串
    void testVehicleNullShift() {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(1));

        VehicleData d = VehicleData::fromJson(o);
        QVERIFY(!d.shiftStart.isValid());
        QVERIFY(!d.shiftEnd.isValid());
        QCOMPARE(d.shiftStart.toString(), QString(""));
        QCOMPARE(d.shiftEnd.toString(), QString(""));
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — VehicleTypeData fromJson
    void testVehicleTypeFromJson() {
        QJsonObject o;
        o["name"] = "冷藏车";
        o["maxWeight"] = 5000.0;
        o["maxVolume"] = 30.0;
        o["speed"] = 60.0;

        VehicleTypeData d = VehicleTypeData::fromJson(o);
        QCOMPARE(d.name, "冷藏车");
        QCOMPARE(d.maxWeight, 5000.0);
        QCOMPARE(d.maxVolume, 30.0);
        QCOMPARE(d.speed, 60.0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 完整ScheduleResult JSON解析
    void testScheduleResultFromJson() {
        QJsonObject o;
        {
            QJsonArray vehicles;
            QJsonObject vr;
            vr["vehicleId"] = QJsonValue::fromVariant(QVariant(1));
            QJsonArray route;
            {
                QJsonObject leg;
                leg["seq"] = 1;
                leg["orderId"] = QJsonValue::fromVariant(QVariant(100));
                leg["plannedStart"] = "2025-07-09T08:00:00";
                leg["plannedEnd"] = "2025-07-09T09:30:00";
                leg["idleBefore"] = 0;
                leg["loadWeight"] = 150.0;
                leg["loadVolume"] = 2.0;
                route.append(leg);
            }
            {
                QJsonObject leg;
                leg["seq"] = 2;
                leg["orderId"] = QJsonValue::fromVariant(QVariant(101));
                leg["plannedStart"] = "2025-07-09T10:00:00";
                leg["plannedEnd"] = "2025-07-09T11:00:00";
                leg["idleBefore"] = 1800;
                leg["loadWeight"] = 300.0;
                leg["loadVolume"] = 4.0;
                route.append(leg);
            }
            vr["route"] = route;
            vehicles.append(vr);
            o["vehicles"] = vehicles;
        }
        {
            QJsonArray unassigned;
            unassigned.append(QJsonValue::fromVariant(QVariant(200)));
            unassigned.append(QJsonValue::fromVariant(QVariant(201)));
            o["unassigned"] = unassigned;
        }
        {
            QJsonObject stats;
            stats["totalIdle"] = 1800;
            stats["totalRevenue"] = 520.5;
            stats["feasible"] = true;
            o["stats"] = stats;
        }

        ScheduleResult r = ScheduleResult::fromJson(o);
        QCOMPARE(r.vehicles.size(), 1);
        QCOMPARE(r.vehicles[0].vehicleId, Q_UINT64_C(1));
        QCOMPARE(r.vehicles[0].route.size(), 2);
        QCOMPARE(r.vehicles[0].route[0].seq, 1);
        QCOMPARE(r.vehicles[0].route[0].orderId, Q_UINT64_C(100));
        QCOMPARE(r.vehicles[0].route[1].seq, 2);
        QCOMPARE(r.vehicles[0].route[1].idleBefore, 1800);

        QCOMPARE(r.unassigned.size(), 2);
        QCOMPARE(r.unassigned[0], Q_UINT64_C(200));
        QCOMPARE(r.unassigned[1], Q_UINT64_C(201));

        QCOMPARE(r.stats.totalIdle, 1800);
        QCOMPARE(r.stats.totalRevenue, 520.5);
        QVERIFY(r.stats.feasible);
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 空的ScheduleResult JSON
    void testScheduleResultEmpty() {
        QJsonObject o;
        o["vehicles"] = QJsonArray();
        o["unassigned"] = QJsonArray();
        {
            QJsonObject stats;
            stats["totalIdle"] = 0;
            stats["totalRevenue"] = 0;
            stats["feasible"] = false;
            o["stats"] = stats;
        }

        ScheduleResult r = ScheduleResult::fromJson(o);
        QVERIFY(r.vehicles.isEmpty());
        QVERIFY(r.unassigned.isEmpty());
        QCOMPARE(r.stats.totalIdle, 0);
        QCOMPARE(r.stats.totalRevenue, 0.0);
        QVERIFY(!r.stats.feasible);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 负收益值正确读取
    void testOrderNegativeRevenue() {
        QJsonObject o;
        o["revenue"] = -100.0;
        o["status"] = "CANCELLED";

        OrderData d = OrderData::fromJson(o);
        QCOMPARE(d.revenue, -100.0);
        QCOMPARE(d.status, "CANCELLED");
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 极端经纬度值
    void testLocationExtremeCoords() {
        QJsonObject o;
        o["lng"] = 180.0;
        o["lat"] = 90.0;

        LocationData d = LocationData::fromJson(o);
        QCOMPARE(d.lng, 180.0);
        QCOMPARE(d.lat, 90.0);
    }
};

QTEST_MAIN(TestModels)
#include "test_models.moc"
