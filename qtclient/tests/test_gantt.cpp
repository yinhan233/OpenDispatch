#include <QtTest>
#include <QWidget>
#include <QPainter>
#include "widgets/GanttChart.h"
#include "Models.h"

static ScheduleResult buildTestSchedule(int numVehicles, int legsPerVehicle) {
    ScheduleResult r;
    r.stats.feasible = true;
    r.stats.totalIdle = 0;
    r.stats.totalRevenue = 1000.0;
    for (int v = 0; v < numVehicles; ++v) {
        VehicleRoute vr;
        vr.vehicleId = static_cast<qulonglong>(v + 1);
        for (int l = 0; l < legsPerVehicle; ++l) {
            RouteLeg leg;
            leg.seq = l + 1;
            leg.orderId = static_cast<qulonglong>(100 + v * 10 + l);
            leg.plannedStart = QDateTime::currentDateTime().addSecs(3600 * l);
            leg.plannedEnd = QDateTime::currentDateTime().addSecs(3600 * l + 1800);
            leg.idleBefore = (l == 0) ? 0 : 600;
            leg.loadWeight = 100.0;
            leg.loadVolume = 2.0;
            vr.route.append(leg);
        }
        r.vehicles.append(vr);
    }
    return r;
}

class TestGanttChart : public QObject {
    Q_OBJECT

private:
    QWidget *m_parent = nullptr;
    GanttChart *m_chart = nullptr;

private slots:
    void init() {
        m_parent = new QWidget();
        m_parent->hide();
        m_chart = new GanttChart(m_parent);
    }

    void cleanup() {
        delete m_parent;
        m_parent = nullptr;
        m_chart = nullptr;
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 新创建的GanttChart有默认最小尺寸
    void testDefaultState() {
        QVERIFY(m_chart->minimumWidth() >= 600);
        QVERIFY(m_chart->minimumHeight() >= 300);
        QVERIFY(m_chart->sizeHint().width() >= 600);
        QVERIFY(m_chart->sizeHint().height() >= 300);
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 空调度数据不崩溃
    void testSetEmptySchedule() {
        ScheduleResult empty;
        m_chart->setSchedule(empty);
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 单车辆单leg
    void testSetSingleVehicle() {
        ScheduleResult r = buildTestSchedule(1, 1);
        m_chart->setSchedule(r);
        QVERIFY(m_chart->sizeHint().width() > 0);
        QVERIFY(m_chart->sizeHint().height() > 0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 多车辆，高度随车辆数缩放
    void testSetMultipleVehicles() {
        ScheduleResult r = buildTestSchedule(3, 2);
        m_chart->setSchedule(r);
        QSize hint = m_chart->sizeHint();
        QVERIFY(hint.width() > 0);
        QVERIFY(hint.height() > 0);

        // 更多车辆的height应该更大
        ScheduleResult rMore = buildTestSchedule(5, 2);
        m_chart->setSchedule(rMore);
        QSize hintMore = m_chart->sizeHint();
        QVERIFY(hintMore.height() >= hint.height());
    }

    // 测试类型: 白盒测试
    // 测试方法: 等价类划分 — 含未分配订单不崩溃
    void testSetWithUnassigned() {
        ScheduleResult r = buildTestSchedule(1, 1);
        r.unassigned.append(100);
        r.unassigned.append(200);
        m_chart->setSchedule(r);
        QVERIFY(true);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — setSchedule后clear回到默认状态
    void testClear() {
        ScheduleResult r = buildTestSchedule(2, 3);
        m_chart->setSchedule(r);
        QSize hintAfterSet = m_chart->sizeHint();

        m_chart->clear();
        QSize hintAfterClear = m_chart->sizeHint();
        QVERIFY(hintAfterClear.height() <= hintAfterSet.height());
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — sizeHint返回有效尺寸
    void testSizeHint() {
        QSize hint = m_chart->sizeHint();
        QVERIFY(hint.width() >= 600);
        QVERIFY(hint.height() >= 300);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — minimumSizeHint返回有效正尺寸
    void testMinimumSizeHint() {
        QSize minHint = m_chart->minimumSizeHint();
        QVERIFY(minHint.width() > 0);
        QVERIFY(minHint.height() > 0);
    }

    // 测试类型: 白盒测试
    // 测试方法: 边界值分析 — 无数据时绘制不崩溃
    void testNoCrashOnPaint() {
        m_parent->resize(800, 400);
        m_chart->resize(800, 400);
        m_chart->show();
        QTest::qWait(50);
        QVERIFY(true);
    }
};

QTEST_MAIN(TestGanttChart)
#include "test_gantt.moc"
