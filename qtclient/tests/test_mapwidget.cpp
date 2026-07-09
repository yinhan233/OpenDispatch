#include <QtTest>
#include "widgets/MapWidget.h"

static QList<AnimLocation> makeOneLocation() {
    AnimLocation loc;
    loc.id = 1;
    loc.name = "测试点";
    loc.lng = 114.30;
    loc.lat = 30.59;
    return {loc};
}

static QList<AnimLocation> makeMultipleLocations() {
    AnimLocation loc1;
    loc1.id = 1;
    loc1.name = "武汉";
    loc1.lng = 114.30;
    loc1.lat = 30.59;

    AnimLocation loc2;
    loc2.id = 2;
    loc2.name = "北京";
    loc2.lng = 116.40;
    loc2.lat = 39.90;

    AnimLocation loc3;
    loc3.id = 3;
    loc3.name = "上海";
    loc3.lng = 121.47;
    loc3.lat = 31.23;

    return {loc1, loc2, loc3};
}

static QList<AnimOrder> makeOneOrder() {
    AnimOrder o;
    o.id = 100;
    o.pickupLocId = 1;
    o.deliveryLocId = 2;
    o.serviceTime = 30;
    return {o};
}

static QList<AnimVehicle> makeOneVehicle() {
    AnimVehicle v;
    v.id = 1;
    v.toolType = "微型面包车";
    v.status = "IDLE";
    v.curLocId = 1;
    v.speed = 30.0;
    return {v};
}

static QList<AnimVehicleRoute> makeOneRoute() {
    AnimRouteLeg leg;
    leg.orderId = 100;
    leg.plannedStart = QDateTime::currentDateTime().addSecs(3600);
    leg.plannedEnd = QDateTime::currentDateTime().addSecs(7200);
    leg.idleBefore = 0;

    AnimVehicleRoute vr;
    vr.vehicleId = 1;
    vr.route.append(leg);
    return {vr};
}

class TestMapWidget : public QObject {
    Q_OBJECT

private slots:
    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — MapWidget可构造
    void testConstructor() {
        MapWidget *w = new MapWidget();
        QVERIFY(w != nullptr);
        QVERIFY(w->minimumWidth() >= 600);
        QVERIFY(w->minimumHeight() >= 400);
        delete w;
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — setData空数据不崩溃
    void testSetEmptyData() {
        MapWidget w;
        w.setData({}, {}, {}, {});
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 单个地点不崩溃
    void testSetSinglePoint() {
        MapWidget w;
        w.setData(makeOneLocation(), {}, {}, {});
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 多个地点+订单+车辆+路径不崩溃
    void testSetMultiplePoints() {
        MapWidget w;
        w.setData(makeMultipleLocations(), makeOneOrder(), makeOneVehicle(), makeOneRoute());
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — play/pause/reset不崩溃
    void testPlayPauseReset() {
        MapWidget w;
        w.play();
        QVERIFY(true);
        w.pause();
        QVERIFY(true);
        w.reset();
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — setSpeed不崩溃
    void testSetSpeed() {
        MapWidget w;
        w.setSpeed(2.0);
        QVERIFY(true);
        w.setSpeed(10.0);
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — setData后clear不崩溃
    void testClear() {
        MapWidget w;
        w.setData(makeMultipleLocations(), makeOneOrder(), makeOneVehicle(), makeOneRoute());
        w.clear();
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 构造后尺寸合理
    void testDefaultDimensions() {
        MapWidget w;
        QVERIFY(w.width() >= 0);
        QVERIFY(w.height() >= 0);
        QVERIFY(w.minimumWidth() >= 600);
        QVERIFY(w.minimumHeight() >= 400);
    }
};

QTEST_MAIN(TestMapWidget)
#include "test_mapwidget.moc"
