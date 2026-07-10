#include "ScheduleTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QLineEdit>
#include <QMessageBox>
#include <QLabel>
#include <QFrame>
#include <QGridLayout>
#include <QScrollArea>
#include <QSizePolicy>

static QString btnPrimary() {
    return "QPushButton { background: #1C1917; color: #FFFFFF; border: none; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #3F3F46; }"
           "QPushButton:pressed { background: #0C0A09; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}
static QString btnSuccess() {
    return "QPushButton { background: #15803D; color: #FFFFFF; border: none; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #166534; }"
           "QPushButton:pressed { background: #14532D; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}
static QString btnSecondary() {
    return "QPushButton { background: #F5F4F1; color: #1C1917; border: 1px solid #D6D3CB; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #ECEBE8; border-color: #A8A29E; }"
           "QPushButton:pressed { background: #E7E5E0; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}

ScheduleTab::ScheduleTab(ApiClient *api, QWidget *parent)
    : QWidget(parent), m_api(api) {
    auto *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(20, 18, 20, 18);
    mainLayout->setSpacing(14);

    //页头
    auto *headerLayout = new QHBoxLayout;
    headerLayout->setSpacing(12);
    auto *title = new QLabel("调度方案");
    title->setProperty("cssClass", "title");
    headerLayout->addWidget(title);
    headerLayout->addStretch();
    mainLayout->addLayout(headerLayout);

    // 内容区域 — 可滚动
    auto *scrollArea = new QScrollArea;
    scrollArea->setWidgetResizable(true);
    scrollArea->setFrameShape(QFrame::NoFrame);
    scrollArea->setStyleSheet("QScrollArea { background: transparent; border: none; }");

    auto *scrollContent = new QWidget;
    auto *contentLayout = new QVBoxLayout(scrollContent);
    contentLayout->setContentsMargins(0, 0, 0, 0);
    contentLayout->setSpacing(14);

    //操作按钮卡片
    auto *actionGroup = new QGroupBox("  调度操作  ");
    auto *actionLayout = new QHBoxLayout(actionGroup);
    actionLayout->setSpacing(8);
    actionLayout->setContentsMargins(12, 14, 12, 10);

    m_runBtn = new QPushButton("执行调度");
    m_runBtn->setMinimumWidth(90);
    m_runBtn->setStyleSheet(btnPrimary());
    m_rescheduleBtn = new QPushButton("动态重调度");
    m_rescheduleBtn->setMinimumWidth(100);
    m_rescheduleBtn->setStyleSheet(btnSuccess());
    m_dryRunBtn = new QPushButton("静态预览");
    m_dryRunBtn->setMinimumWidth(80);
    m_dryRunBtn->setStyleSheet(btnSecondary());
    m_dryRunDynBtn = new QPushButton("动态预览");
    m_dryRunDynBtn->setMinimumWidth(80);
    m_dryRunDynBtn->setStyleSheet(btnSecondary());
    m_currentBtn = new QPushButton("查看当前");
    m_currentBtn->setMinimumWidth(80);
    m_currentBtn->setStyleSheet(btnSecondary());
    actionLayout->addWidget(m_runBtn);
    actionLayout->addWidget(m_rescheduleBtn);
    actionLayout->addWidget(m_dryRunBtn);
    actionLayout->addWidget(m_dryRunDynBtn);
    actionLayout->addWidget(m_currentBtn);
    actionLayout->addStretch();

    contentLayout->addWidget(actionGroup);

    //完成订单卡片
    auto *completeGroup = new QGroupBox("  订单完成  ");
    auto *completeLayout = new QHBoxLayout(completeGroup);
    completeLayout->setSpacing(8);
    completeLayout->setContentsMargins(12, 14, 12, 10);
    completeLayout->addWidget(new QLabel("车辆 ID"));
    m_vehicleIdEdit = new QLineEdit;
    m_vehicleIdEdit->setMaximumWidth(90);
    m_vehicleIdEdit->setPlaceholderText("车辆ID");
    completeLayout->addWidget(m_vehicleIdEdit);
    completeLayout->addSpacing(8);
    completeLayout->addWidget(new QLabel("订单 ID"));
    m_orderIdEdit = new QLineEdit;
    m_orderIdEdit->setMaximumWidth(90);
    m_orderIdEdit->setPlaceholderText("订单ID");
    completeLayout->addWidget(m_orderIdEdit);
    completeLayout->addSpacing(12);
    m_completeBtn = new QPushButton("标记完成");
    m_completeBtn->setStyleSheet(btnSuccess());
    completeLayout->addWidget(m_completeBtn);
    completeLayout->addStretch();
    contentLayout->addWidget(completeGroup);

    // 统计指标卡片
    auto *statsGroup = new QGroupBox("  调度结果  ");
    auto *statsLayout = new QGridLayout(statsGroup);
    statsLayout->setSpacing(10);
    statsLayout->setContentsMargins(12, 14, 12, 10);

    auto makeMetric = [](const QString &label, const QString &value, const QString &color) {
        auto *w = new QWidget;
        w->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
        w->setStyleSheet(
            "QWidget { background: #FAFAF9; border: 1px solid #E7E5E0; "
            "border-radius: 8px; }");
        auto *l = new QVBoxLayout(w);
        l->setSpacing(2);
        l->setContentsMargins(16, 14, 16, 14);
        auto *v = new QLabel(value);
        v->setStyleSheet(QString("font-size: 24px; font-weight: 700; color: %1; background: transparent; border: none;").arg(color));
        auto *n = new QLabel(label);
        n->setStyleSheet("font-size: 10px; font-weight: 600; color: #57534E; text-transform: uppercase; letter-spacing: 1px; background: transparent; border: none;");
        l->addWidget(v);
        l->addWidget(n);
        return w;
    };

    m_idleMetric = makeMetric("总闲置 (秒)", "--", "#1C1917");
    m_revenueMetric = makeMetric("总收益 (元)", "--", "#1C1917");
    m_feasibleMetric = makeMetric("可行性", "--", "#57534E");
    m_vehiclesMetric = makeMetric("调度车辆", "--", "#1C1917");
    m_unassignedMetric = makeMetric("未分配订单", "--", "#9F2F2D");

    statsLayout->addWidget(m_idleMetric, 0, 0);
    statsLayout->addWidget(m_revenueMetric, 0, 1);
    statsLayout->addWidget(m_vehiclesMetric, 0, 2);
    statsLayout->addWidget(m_unassignedMetric, 0, 3);
    statsLayout->addWidget(m_feasibleMetric, 0, 4);
    statsLayout->setColumnStretch(0, 1);
    statsLayout->setColumnStretch(1, 1);
    statsLayout->setColumnStretch(2, 1);
    statsLayout->setColumnStretch(3, 1);
    statsLayout->setColumnStretch(4, 1);

    contentLayout->addWidget(statsGroup);

    //甘特图
    auto *ganttLabel = new QLabel("调度甘特图");
    ganttLabel->setProperty("cssClass", "sectionLabel");
    contentLayout->addWidget(ganttLabel);

    m_gantt = new GanttChart;
    m_gantt->setStyleSheet("QFrame { border: 1px solid #E7E5E0; border-radius: 8px; background: #FFFFFF; }");
    auto *ganttScroll = new QScrollArea;
    ganttScroll->setWidget(m_gantt);
    ganttScroll->setWidgetResizable(true);
    ganttScroll->setFrameShape(QFrame::NoFrame);
    ganttScroll->setStyleSheet("QScrollArea { background: transparent; border: 1px solid #E7E5E0; border-radius: 8px; }");
    contentLayout->addWidget(ganttScroll, 3);

    //未分配订单
    auto *unassignedLabel = new QLabel("未分配订单");
    unassignedLabel->setProperty("cssClass", "sectionLabel");
    contentLayout->addWidget(unassignedLabel);

    m_unassignedList = new QListWidget;
    m_unassignedList->setMaximumHeight(90);
    m_unassignedList->setStyleSheet(
        "QListWidget { border: 1px solid #E7E5E0; border-radius: 8px; background: #FFFFFF; }");
    contentLayout->addWidget(m_unassignedList);

    scrollArea->setWidget(scrollContent);
    mainLayout->addWidget(scrollArea, 1);

    connect(m_runBtn, &QPushButton::clicked, this, &ScheduleTab::onRun);
    connect(m_dryRunBtn, &QPushButton::clicked, this, &ScheduleTab::onDryRun);
    connect(m_rescheduleBtn, &QPushButton::clicked, this, &ScheduleTab::onReschedule);
    connect(m_dryRunDynBtn, &QPushButton::clicked, this, &ScheduleTab::onDryRunDynamic);
    connect(m_completeBtn, &QPushButton::clicked, this, &ScheduleTab::onComplete);
    connect(m_currentBtn, &QPushButton::clicked, this, &ScheduleTab::onCurrent);
}

void ScheduleTab::refresh() {
}

void ScheduleTab::onRun() {
    m_runBtn->setEnabled(false);
    m_api->triggerSchedule([this](QJsonObject obj) {
        m_lastResult = ScheduleResult::fromJson(obj);
        m_gantt->setSchedule(m_lastResult);
        updateMetrics();
        m_runBtn->setEnabled(true);
    });
}

void ScheduleTab::onDryRun() {
    m_dryRunBtn->setEnabled(false);
    m_api->dryRunSchedule([this](QJsonObject obj) {
        m_lastResult = ScheduleResult::fromJson(obj);
        m_gantt->setSchedule(m_lastResult);
        updateMetrics();
        m_dryRunBtn->setEnabled(true);
    });
}

void ScheduleTab::onReschedule() {
    m_rescheduleBtn->setEnabled(false);
    m_api->reschedule([this](QJsonObject obj) {
        m_lastResult = ScheduleResult::fromJson(obj);
        m_gantt->setSchedule(m_lastResult);
        updateMetrics();
        m_rescheduleBtn->setEnabled(true);
    });
}

void ScheduleTab::onDryRunDynamic() {
    m_dryRunDynBtn->setEnabled(false);
    m_api->dryRunDynamic([this](QJsonObject obj) {
        m_lastResult = ScheduleResult::fromJson(obj);
        m_gantt->setSchedule(m_lastResult);
        updateMetrics();
        m_dryRunDynBtn->setEnabled(true);
    });
}

void ScheduleTab::onComplete() {
    bool ok1 = false, ok2 = false;
    qulonglong vid = m_vehicleIdEdit->text().toULongLong(&ok1);
    qulonglong oid = m_orderIdEdit->text().toULongLong(&ok2);
    if (!ok1 || !ok2) {
        QMessageBox::warning(this, "输入错误", "车辆ID和订单ID必须为正整数。");
        return;
    }
    m_completeBtn->setEnabled(false);
    m_api->completeOrder(vid, oid, [this]() {
        QMessageBox::information(this, "完成", "订单已标记为完成。");
        m_completeBtn->setEnabled(true);
        m_vehicleIdEdit->clear();
        m_orderIdEdit->clear();
    });
}

void ScheduleTab::onCurrent() {
    m_currentBtn->setEnabled(false);
    m_api->currentSchedule([this](QJsonObject obj) {
        m_lastResult = ScheduleResult::fromJson(obj);
        m_gantt->setSchedule(m_lastResult);
        updateMetrics();
        m_currentBtn->setEnabled(true);
    });
}

void ScheduleTab::updateMetrics() {
    auto setMetric = [](QWidget *w, const QString &value, const QString &color) {
        auto *l = w->layout()->itemAt(0)->widget();
        l->setStyleSheet(QString("font-size: 24px; font-weight: 700; color: %1; background: transparent; border: none;").arg(color));
        if (auto *lbl = qobject_cast<QLabel*>(l)) lbl->setText(value);
    };

    QString feasibleColor = m_lastResult.stats.feasible ? "#15803D" : "#9F2F2D";
    QString feasibleText = m_lastResult.stats.feasible ? "可行" : "不可行";
    int unassignedCount = m_lastResult.unassigned.size();

    setMetric(m_idleMetric, QString::number(m_lastResult.stats.totalIdle), "#1C1917");
    setMetric(m_revenueMetric, QString::number(m_lastResult.stats.totalRevenue, 'f', 1), "#1C1917");
    setMetric(m_feasibleMetric, feasibleText, feasibleColor);
    setMetric(m_vehiclesMetric, QString::number(m_lastResult.vehicles.size()), "#1C1917");
    setMetric(m_unassignedMetric, QString::number(unassignedCount),
              unassignedCount > 0 ? "#9F2F2D" : "#15803D");

    m_unassignedList->clear();
    for (qulonglong id : m_lastResult.unassigned) {
        m_unassignedList->addItem(QString("订单 #%1").arg(id));
    }
}
