#include <QtTest>
#include <QString>
#include "Theme.h"

class TestTheme : public QObject {
    Q_OBJECT

private slots:
    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — globalStyleSheet返回非空
    void testStyleSheetNotEmpty() {
        QString ss = Theme::globalStyleSheet();
        QVERIFY(!ss.isEmpty());
        QVERIFY(ss.length() > 100);
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 样式表包含核心控件选择器
    void testContainsSelectors() {
        QString ss = Theme::globalStyleSheet();
        QVERIFY(ss.contains("QPushButton"));
        QVERIFY(ss.contains("QTableWidget"));
        QVERIFY(ss.contains("QLineEdit"));
        QVERIFY(ss.contains("QLabel"));
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 样式表包含cssClass属性选择器
    void testContainsCssClasses() {
        QString ss = Theme::globalStyleSheet();
        QVERIFY(ss.contains("cssClass=\"secondary\""));
        QVERIFY(ss.contains("cssClass=\"danger\""));
        QVERIFY(ss.contains("cssClass=\"success\""));
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 调色板颜色函数返回有效QColor
    void testPaletteDefined() {
        QVERIFY(Theme::Colors::Background().isValid());
        QVERIFY(Theme::Colors::Primary().isValid());
        QVERIFY(Theme::Colors::Danger().isValid());
        QVERIFY(Theme::Colors::Success().isValid());
        QVERIFY(Theme::Colors::Warning().isValid());
        QVERIFY(Theme::Colors::TextPrimary().isValid());
        QVERIFY(Theme::Colors::Border().isValid());
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 样式表包含QTabWidget样式
    void testContainsTabWidget() {
        QString ss = Theme::globalStyleSheet();
        QVERIFY(ss.contains("QTabWidget"));
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — 样式表包含QScrollBar样式
    void testContainsScrollBar() {
        QString ss = Theme::globalStyleSheet();
        QVERIFY(ss.contains("QScrollBar"));
    }

    // 测试类型: 白盒测试
    // 测试方法: 语句覆盖 — orderColor按索引循环返回不同颜色
    void testOrderColorCycles() {
        QColor c0 = Theme::Colors::orderColor(0);
        QColor c1 = Theme::Colors::orderColor(1);
        QColor c8 = Theme::Colors::orderColor(8);
        QVERIFY(c0.isValid());
        QCOMPARE(c0, c8); // 8 % 8 == 0, 应循环
    }
};

QTEST_MAIN(TestTheme)
#include "test_theme.moc"
