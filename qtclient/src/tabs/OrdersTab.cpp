#include "OrdersTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QHeaderView>
#include <QMessageBox>
#include <QFrame>
#include <QLabel>
#include <QGridLayout>

static QString btnSecondary() {
    return "QPushButton { background: #F5F4F1; color: #1C1917; border: 1px solid #D6D3CB; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #ECEBE8; border-color: #A8A29E; }"
           "QPushButton:pressed { background: #E7E5E0; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}
static QString btnGhost() {
    return "QPushButton { background: transparent; color: #57534E; border: none; "
           "border-radius: 6px; padding: 8px 14px; font-size: 13px; font-weight: 500; }"
           "QPushButton:hover { background: #F5F4F1; color: #1C1917; }";
}
static QString btnDanger() {
    return "QPushButton { background: #9F2F2D; color: #FFFFFF; border: none; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #8B2725; }"
           "QPushButton:pressed { background: #7C201F; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}

OrdersTab::OrdersTab(ApiClient *api, QWidget *parent)
    : QWidget(parent), m_api(api) {
    auto *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(20, 18, 20, 18);
    mainLayout->setSpacing(14);

    // ----- 页头 -----
    auto *headerLayout = new QHBoxLayout;
    headerLayout->setSpacing(12);
    auto *title = new QLabel("订单管理");
    title->setProperty("cssClass", "title");
    headerLayout->addWidget(title);
    headerLayout->addStretch();
    mainLayout->addLayout(headerLayout);

    // ----- 表单卡片 (双列) -----
    auto *formGroup = new QGroupBox("  新增订单  ");

    m_pickupLocEdit = new QLineEdit;
    m_pickupLocEdit->setPlaceholderText("取货地点 ID");
    m_deliveryLocEdit = new QLineEdit;
    m_deliveryLocEdit->setPlaceholderText("送货地点 ID");
    m_timeStartEdit = new QDateTimeEdit(QDateTime::currentDateTime());
    m_timeStartEdit->setDisplayFormat("yyyy-MM-dd HH:mm");
    m_timeStartEdit->setCalendarPopup(true);
    m_timeEndEdit = new QDateTimeEdit(QDateTime::currentDateTime().addSecs(3600));
    m_timeEndEdit->setDisplayFormat("yyyy-MM-dd HH:mm");
    m_timeEndEdit->setCalendarPopup(true);
    m_revenueEdit = new QDoubleSpinBox;
    m_revenueEdit->setRange(0, 1e7);
    m_revenueEdit->setDecimals(2);
    m_revenueEdit->setSuffix(" 元");
    m_weightEdit = new QDoubleSpinBox;
    m_weightEdit->setRange(0, 1e6);
    m_weightEdit->setDecimals(2);
    m_weightEdit->setSuffix(" kg");
    m_volumeEdit = new QDoubleSpinBox;
    m_volumeEdit->setRange(0, 1e6);
    m_volumeEdit->setDecimals(2);
    m_volumeEdit->setSuffix(" m³");
    m_serviceEdit = new QSpinBox;
    m_serviceEdit->setRange(0, 86400);
    m_serviceEdit->setValue(60);
    m_serviceEdit->setSuffix(" 秒");
    m_serviceEdit->setToolTip("装卸货服务时长:司机在取货点装货/送货点卸货的预计耗时");

    // 用嵌套布局实现双列表单
    auto *grid = new QGridLayout;
    grid->setSpacing(10);
    grid->setContentsMargins(0, 0, 0, 0);
    auto addField = [&](int row, int col, const QString &label, QWidget *w) {
        auto *l = new QLabel(label);
        l->setAlignment(Qt::AlignRight | Qt::AlignVCenter);
        l->setStyleSheet("color: #57534E; font-size: 12px; font-weight: 500;");
        grid->addWidget(l, row, col * 2);
        grid->addWidget(w, row, col * 2 + 1);
    };
    addField(0, 0, "取货地点 ID", m_pickupLocEdit);
    addField(0, 1, "送货地点 ID", m_deliveryLocEdit);
    addField(1, 0, "最早取货时间", m_timeStartEdit);
    addField(1, 1, "最晚送达时间", m_timeEndEdit);
    addField(2, 0, "收益", m_revenueEdit);
    addField(2, 1, "装卸货时长", m_serviceEdit);
    addField(3, 0, "重量", m_weightEdit);
    addField(3, 1, "体积", m_volumeEdit);
    grid->setColumnStretch(1, 1);
    grid->setColumnStretch(3, 1);

    m_addBtn = new QPushButton("添加订单");
    m_addBtn->setStyleSheet(btnSecondary());
    auto *btnRow = new QHBoxLayout;
    btnRow->setSpacing(8);
    btnRow->addStretch();
    btnRow->addWidget(m_addBtn);

    auto *formWrap = new QVBoxLayout;
    formWrap->setSpacing(12);
    formWrap->setContentsMargins(12, 14, 12, 10);
    formWrap->addLayout(grid);
    formWrap->addLayout(btnRow);
    formGroup->setLayout(formWrap);

    mainLayout->addWidget(formGroup);

    // ----- 表格工具栏 -----
    auto *tableToolbar = new QHBoxLayout;
    tableToolbar->setSpacing(8);

    auto *sectionLabel = new QLabel("订单列表");
    sectionLabel->setProperty("cssClass", "sectionLabel");
    tableToolbar->addWidget(sectionLabel);
    tableToolbar->addSpacing(16);

    auto *statusLabel = new QLabel("状态:");
    statusLabel->setProperty("cssClass", "sectionLabel");
    tableToolbar->addWidget(statusLabel);
    m_statusCombo = new QComboBox;
    m_statusCombo->addItem("UNASSIGNED");
    m_statusCombo->addItem("ASSIGNED");
    m_statusCombo->addItem("EXECUTING");
    m_statusCombo->addItem("DONE");
    m_statusCombo->addItem("CANCELLED");
    m_statusCombo->setMaximumWidth(140);
    tableToolbar->addWidget(m_statusCombo);

    m_setStatusBtn = new QPushButton("设置状态");
    m_setStatusBtn->setStyleSheet(btnSecondary());
    m_setStatusBtn->setEnabled(false);
    m_setStatusBtn->setToolTip("将选中订单的状态设置为下拉框中的值");
    tableToolbar->addWidget(m_setStatusBtn);

    tableToolbar->addSpacing(8);

    tableToolbar->addStretch();
    m_refreshBtn = new QPushButton("刷新");
    m_refreshBtn->setStyleSheet(btnGhost());
    m_deleteBtn = new QPushButton("删除选中");
    m_deleteBtn->setStyleSheet(btnDanger());
    m_deleteBtn->setEnabled(false);
    tableToolbar->addWidget(m_refreshBtn);
    tableToolbar->addWidget(m_deleteBtn);

    mainLayout->addLayout(tableToolbar);

    // ----- 表格 -----
    m_table = new QTableWidget;
    m_table->setColumnCount(10);
    m_table->setHorizontalHeaderLabels(
        {"ID", "取货地", "送货地", "取货时间", "送达时间",
         "收益", "重量", "体积", "状态", "分配车辆"});
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_table->setAlternatingRowColors(true);
    m_table->verticalHeader()->setVisible(false);
    m_table->setShowGrid(false);
    m_table->verticalHeader()->setDefaultSectionSize(36);
    m_table->setStyleSheet("QTableWidget { border: 1px solid #E7E5E0; border-radius: 8px; }");
    m_table->horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);

    mainLayout->addWidget(m_table, 1);

    connect(m_addBtn, &QPushButton::clicked, this, &OrdersTab::onAdd);
    connect(m_deleteBtn, &QPushButton::clicked, this, &OrdersTab::onDelete);
    connect(m_refreshBtn, &QPushButton::clicked, this, &OrdersTab::refresh);
    connect(m_setStatusBtn, &QPushButton::clicked, this, &OrdersTab::onSetStatus);
    connect(m_table->selectionModel(), &QItemSelectionModel::selectionChanged,
            this, &OrdersTab::onSelectionChanged);

    refresh();
}

void OrdersTab::refresh() {
    m_api->getOrders([this](QList<QJsonObject> list) {
        m_orders.clear();
        for (const auto &o : list) m_orders.append(OrderData::fromJson(o));
        m_table->setRowCount(m_orders.size());
        for (int i = 0; i < m_orders.size(); ++i) {
            const auto &o = m_orders[i];
            m_table->setItem(i, 0, new QTableWidgetItem(QString::number(o.id)));
            m_table->setItem(i, 1, new QTableWidgetItem(QString::number(o.pickupLocId)));
            m_table->setItem(i, 2, new QTableWidgetItem(QString::number(o.deliveryLocId)));
            m_table->setItem(i, 3, new QTableWidgetItem(o.timeStart.toString("MM-dd HH:mm")));
            m_table->setItem(i, 4, new QTableWidgetItem(o.timeEnd.toString("MM-dd HH:mm")));
            m_table->setItem(i, 5, new QTableWidgetItem(QString::number(o.revenue, 'f', 1)));
            m_table->setItem(i, 6, new QTableWidgetItem(QString::number(o.weight, 'f', 1)));
            m_table->setItem(i, 7, new QTableWidgetItem(QString::number(o.volume, 'f', 1)));

            // 状态列: 徽章式着色
            auto *statusItem = new QTableWidgetItem(o.status);
            statusItem->setTextAlignment(Qt::AlignCenter);
            QColor statusColor;
            QString statusBg;
            if (o.status == "UNASSIGNED") { statusColor = QColor("#57534E"); statusBg = "#F5F4F1"; }
            else if (o.status == "ASSIGNED") { statusColor = QColor("#1F6C9F"); statusBg = "#E1F3FE"; }
            else if (o.status == "EXECUTING") { statusColor = QColor("#B45309"); statusBg = "#FBF3DB"; }
            else if (o.status == "DONE") { statusColor = QColor("#15803D"); statusBg = "#EDF3EC"; }
            else if (o.status == "CANCELLED") { statusColor = QColor("#9F2F2D"); statusBg = "#FDEBEC"; }
            else { statusColor = QColor("#1C1917"); statusBg = "#F5F4F1"; }
            statusItem->setForeground(statusColor);
            QFont statusFont = statusItem->font();
            statusFont.setBold(true);
            statusItem->setFont(statusFont);
            m_table->setItem(i, 8, statusItem);

            auto *placeholder = new QTableWidgetItem("查询中...");
            placeholder->setTextAlignment(Qt::AlignCenter);
            placeholder->setForeground(QColor("#A8A29E"));
            m_table->setItem(i, 9, placeholder);
            qulonglong oid = o.id;
            int row = i;
            m_api->getOrderVehicle(oid, [this, row, oid](QJsonObject obj) {
                if (row >= m_table->rowCount()) return;
                bool assigned = obj["assigned"].toBool();
                QString text;
                if (assigned) {
                    qulonglong vid = obj["vehicleId"].toVariant().toULongLong();
                    text = QString("车辆 #%1").arg(vid);
                } else {
                    text = "—";
                }
                auto *item = m_table->item(row, 9);
                if (item) {
                    item->setText(text);
                    item->setTextAlignment(Qt::AlignCenter);
                    if (assigned) item->setForeground(QColor("#1C1917"));
                }
            });
        }
    });
}

void OrdersTab::onAdd() {
    bool pok = false, dok = false;
    qulonglong pickupId = m_pickupLocEdit->text().toULongLong(&pok);
    qulonglong deliveryId = m_deliveryLocEdit->text().toULongLong(&dok);
    if (!pok || !dok) {
        QMessageBox::warning(this, "输入错误", "取货和送货地点ID必须为正整数。");
        return;
    }
    if (m_timeStartEdit->dateTime() >= m_timeEndEdit->dateTime()) {
        QMessageBox::warning(this, "输入错误", "取货时间必须早于送达时间。");
        return;
    }

    QJsonObject o;
    o["pickupLocId"] = QJsonValue::fromVariant(QVariant(pickupId));
    o["deliveryLocId"] = QJsonValue::fromVariant(QVariant(deliveryId));
    o["timeStart"] = m_timeStartEdit->dateTime().toString(Qt::ISODate);
    o["timeEnd"] = m_timeEndEdit->dateTime().toString(Qt::ISODate);
    o["revenue"] = m_revenueEdit->value();
    o["weight"] = m_weightEdit->value();
    o["volume"] = m_volumeEdit->value();
    o["serviceTime"] = m_serviceEdit->value();

    m_api->createOrder(o, [this](QJsonObject) { refresh(); });
    m_pickupLocEdit->clear();
    m_deliveryLocEdit->clear();
}

void OrdersTab::onDelete() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    QStringList ids;
    for (const auto &idx : selected)
        ids << QString::number(m_orders[idx.row()].id);
    auto ret = QMessageBox::question(this, "确认删除",
        QString("确定删除 %1 个订单吗？\nID: %2").arg(ids.size()).arg(ids.join(", ")));
    if (ret != QMessageBox::Yes) return;
    m_deleteBtn->setEnabled(false);
    auto *counter = new int(ids.size());
    for (const auto &idx : selected) {
        qulonglong id = m_orders[idx.row()].id;
        m_api->deleteOrder(id, [this, counter]() {
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}

void OrdersTab::onSelectionChanged() {
    bool hasSelection = !m_table->selectionModel()->selectedRows().isEmpty();
    m_deleteBtn->setEnabled(hasSelection);
    m_setStatusBtn->setEnabled(hasSelection);
}

void OrdersTab::onSetStatus() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    QString status = m_statusCombo->currentText();
    auto ret = QMessageBox::question(this, "确认设置状态",
        QString("确定将 %1 个订单的状态设为 %2 吗？").arg(selected.size()).arg(status));
    if (ret != QMessageBox::Yes) return;
    m_setStatusBtn->setEnabled(false);
    auto *counter = new int(selected.size());
    for (const auto &idx : selected) {
        qulonglong id = m_orders[idx.row()].id;
        m_api->setOrderStatus(id, status, [this, counter](QJsonObject obj) {
            Q_UNUSED(obj);
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}
