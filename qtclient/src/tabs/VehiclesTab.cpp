#include "VehiclesTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QHeaderView>
#include <QMessageBox>
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
static QString btnWarning() {
    return "QPushButton { background: #B45309; color: #FFFFFF; border: none; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #92400E; }"
           "QPushButton:pressed { background: #7C2D0A; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}

VehiclesTab::VehiclesTab(ApiClient *api, QWidget *parent)
    : QWidget(parent), m_api(api) {
    auto *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(20, 18, 20, 18);
    mainLayout->setSpacing(14);

    //页头
    auto *headerLayout = new QHBoxLayout;
    headerLayout->setSpacing(12);
    auto *title = new QLabel("车辆管理");
    title->setProperty("cssClass", "title");
    headerLayout->addWidget(title);
    headerLayout->addStretch();
    mainLayout->addLayout(headerLayout);

    //表单卡片
    auto *formGroup = new QGroupBox("  新增车辆  ");
    formGroup->setStyleSheet(formGroup->styleSheet());

    m_typeCombo = new QComboBox;
    m_typeCombo->addItem("-- 请选择车型 --");
    m_personIdEdit = new QLineEdit;
    m_personIdEdit->setPlaceholderText("司机工号 (留空自动生成)");
    m_curLocEdit = new QLineEdit;
    m_curLocEdit->setPlaceholderText("当前地点 ID (默认 0)");
    m_curAvailEdit = new QDateTimeEdit(QDateTime::currentDateTime());
    m_curAvailEdit->setDisplayFormat("yyyy-MM-dd HH:mm");
    m_curAvailEdit->setCalendarPopup(true);
    m_toolTypeEdit = new QLineEdit;
    m_toolTypeEdit->setPlaceholderText("如: 厢式货车");
    m_maxWeightEdit = new QDoubleSpinBox;
    m_maxWeightEdit->setRange(0, 1e6);
    m_maxWeightEdit->setDecimals(2);
    m_maxWeightEdit->setSuffix(" kg");
    m_maxVolumeEdit = new QDoubleSpinBox;
    m_maxVolumeEdit->setRange(0, 1e6);
    m_maxVolumeEdit->setDecimals(2);
    m_maxVolumeEdit->setSuffix(" m³");
    m_speedEdit = new QDoubleSpinBox;
    m_speedEdit->setRange(1, 200);
    m_speedEdit->setDecimals(1);
    m_speedEdit->setSuffix(" km/h");
    m_speedEdit->setValue(30.0);
    m_shiftStartEdit = new QDateTimeEdit(QDateTime::currentDateTime());
    m_shiftStartEdit->setDisplayFormat("yyyy-MM-dd HH:mm");
    m_shiftStartEdit->setCalendarPopup(true);
    m_shiftEndEdit = new QDateTimeEdit(QDateTime::currentDateTime().addSecs(28800));
    m_shiftEndEdit->setDisplayFormat("yyyy-MM-dd HH:mm");
    m_shiftEndEdit->setCalendarPopup(true);
    m_earnedRevenueEdit = new QDoubleSpinBox;
    m_earnedRevenueEdit->setRange(0, 1e9);
    m_earnedRevenueEdit->setDecimals(2);
    m_earnedRevenueEdit->setSuffix(" 元");

    auto *grid = new QGridLayout;
    grid->setSpacing(10);
    auto addField = [&](int row, int col, const QString &label, QWidget *w) {
        auto *l = new QLabel(label);
        l->setAlignment(Qt::AlignRight | Qt::AlignVCenter);
        l->setStyleSheet("color: #57534E; font-size: 12px; font-weight: 500;");
        grid->addWidget(l, row, col * 2);
        grid->addWidget(w, row, col * 2 + 1);
    };
    addField(0, 0, "车辆类型", m_typeCombo);
    addField(0, 1, "司机工号", m_personIdEdit);
    addField(0, 2, "车型名称", m_toolTypeEdit);
    addField(1, 0, "当前地点 ID", m_curLocEdit);
    addField(1, 1, "可用时间", m_curAvailEdit);
    addField(1, 2, "行驶速度", m_speedEdit);
    addField(2, 0, "最大载重", m_maxWeightEdit);
    addField(2, 1, "最大容积", m_maxVolumeEdit);
    addField(2, 2, "已获收益", m_earnedRevenueEdit);
    addField(3, 0, "班次开始", m_shiftStartEdit);
    addField(3, 1, "班次结束", m_shiftEndEdit);
    grid->setColumnStretch(1, 1);
    grid->setColumnStretch(3, 1);
    grid->setColumnStretch(5, 1);

    m_addBtn = new QPushButton("添加车辆");
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

    //表格工具栏
    auto *tableToolbar = new QHBoxLayout;
    tableToolbar->setSpacing(8);

    auto *sectionLabel = new QLabel("车辆列表");
    sectionLabel->setProperty("cssClass", "sectionLabel");
    tableToolbar->addWidget(sectionLabel);
    tableToolbar->addSpacing(16);

    auto *statusLabel = new QLabel("状态:");
    statusLabel->setProperty("cssClass", "sectionLabel");
    tableToolbar->addWidget(statusLabel);
    m_statusCombo = new QComboBox;
    m_statusCombo->addItem("IDLE");
    m_statusCombo->addItem("ON_DUTY");
    m_statusCombo->addItem("OFFLINE");
    m_statusCombo->setMaximumWidth(120);
    tableToolbar->addWidget(m_statusCombo);

    m_setStatusBtn = new QPushButton("设置状态");
    m_setStatusBtn->setStyleSheet(btnSecondary());
    m_setStatusBtn->setEnabled(false);
    m_offlineBtn = new QPushButton("车辆下线");
    m_offlineBtn->setStyleSheet(btnWarning());
    m_offlineBtn->setEnabled(false);
    m_refreshBtn = new QPushButton("刷新");
    m_refreshBtn->setStyleSheet(btnGhost());
    m_deleteBtn = new QPushButton("删除车辆");
    m_deleteBtn->setStyleSheet(btnDanger());
    m_deleteBtn->setEnabled(false);

    tableToolbar->addWidget(m_setStatusBtn);
    tableToolbar->addWidget(m_offlineBtn);
    tableToolbar->addStretch();
    tableToolbar->addWidget(m_refreshBtn);
    tableToolbar->addWidget(m_deleteBtn);

    mainLayout->addLayout(tableToolbar);

    //表格
    m_table = new QTableWidget;
    m_table->setColumnCount(11);
    m_table->setHorizontalHeaderLabels(
        {"ID", "司机", "状态", "地点", "可用时间", "车型",
         "最大载重", "最大容积", "速度", "班次", "已获收益"});
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_table->setAlternatingRowColors(true);
    m_table->verticalHeader()->setVisible(false);
    m_table->setShowGrid(false);
    m_table->verticalHeader()->setDefaultSectionSize(36);
    m_table->setStyleSheet("QTableWidget { border: 1px solid #E7E5E0; border-radius: 8px; }");
    m_table->horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);

    mainLayout->addWidget(m_table, 1);

    connect(m_addBtn, &QPushButton::clicked, this, &VehiclesTab::onAdd);
    connect(m_offlineBtn, &QPushButton::clicked, this, &VehiclesTab::onOffline);
    connect(m_setStatusBtn, &QPushButton::clicked, this, &VehiclesTab::onSetStatus);
    connect(m_deleteBtn, &QPushButton::clicked, this, &VehiclesTab::onDelete);
    connect(m_refreshBtn, &QPushButton::clicked, this, &VehiclesTab::refresh);
    connect(m_typeCombo, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &VehiclesTab::onTypeChanged);
    connect(m_table->selectionModel(), &QItemSelectionModel::selectionChanged,
            this, &VehiclesTab::onSelectionChanged);

    loadTypes();
    refresh();
}

void VehiclesTab::loadTypes() {
    m_api->getVehicleTypes([this](QList<QJsonObject> list) {
        m_types.clear();
        for (const auto &t : list) m_types.append(VehicleTypeData::fromJson(t));
        m_typeCombo->blockSignals(true);
        m_typeCombo->clear();
        m_typeCombo->addItem("-- 请选择车型 --");
        for (const auto &t : m_types) m_typeCombo->addItem(t.name);
        m_typeCombo->blockSignals(false);
    });
}

void VehiclesTab::onTypeChanged(int index) {
    if (index <= 0) return;
    const auto &t = m_types[index - 1];
    m_toolTypeEdit->setText(t.name);
    m_maxWeightEdit->setValue(t.maxWeight);
    m_maxVolumeEdit->setValue(t.maxVolume);
    m_speedEdit->setValue(t.speed);
}

void VehiclesTab::refresh() {
    m_api->getVehicles([this](QList<QJsonObject> list) {
        m_vehicles.clear();
        for (const auto &v : list) m_vehicles.append(VehicleData::fromJson(v));
        m_table->setRowCount(m_vehicles.size());
        for (int i = 0; i < m_vehicles.size(); ++i) {
            const auto &v = m_vehicles[i];
            m_table->setItem(i, 0, new QTableWidgetItem(QString::number(v.id)));
            m_table->setItem(i, 1, new QTableWidgetItem(v.personId));

            // 状态列着色
            auto *statusItem = new QTableWidgetItem(v.status);
            statusItem->setTextAlignment(Qt::AlignCenter);
            QColor c;
            if (v.status == "IDLE") c = QColor("#15803D");
            else if (v.status == "ON_DUTY") c = QColor("#B45309");
            else if (v.status == "OFFLINE") c = QColor("#9F2F2D");
            else c = QColor("#1C1917");
            statusItem->setForeground(c);
            QFont sf = statusItem->font();
            sf.setBold(true);
            statusItem->setFont(sf);
            m_table->setItem(i, 2, statusItem);

            m_table->setItem(i, 3, new QTableWidgetItem(QString::number(v.curLocId)));
            m_table->setItem(i, 4, new QTableWidgetItem(v.curAvailableTime.toString("MM-dd HH:mm")));
            m_table->setItem(i, 5, new QTableWidgetItem(v.toolType));
            m_table->setItem(i, 6, new QTableWidgetItem(QString::number(v.maxWeight, 'f', 1)));
            m_table->setItem(i, 7, new QTableWidgetItem(QString::number(v.maxVolume, 'f', 1)));
            m_table->setItem(i, 8, new QTableWidgetItem(QString::number(v.speed, 'f', 1)));
            QString shift = v.shiftStart.toString("HH:mm") + "-" + v.shiftEnd.toString("HH:mm");
            m_table->setItem(i, 9, new QTableWidgetItem(shift));
            m_table->setItem(i, 10, new QTableWidgetItem(QString::number(v.earnedRevenue, 'f', 1)));
        }
    });
}

void VehiclesTab::onAdd() {
    qulonglong locId = 0;
    QString locText = m_curLocEdit->text().trimmed();
    bool ok = false;
    if (!locText.isEmpty()) {
        locId = locText.toULongLong(&ok);
        if (!ok) {
            QMessageBox::warning(this, "输入错误", "当前地点ID必须为整数。");
            return;
        }
    }

    QJsonObject v;
    v["personId"] = m_personIdEdit->text();
    v["curLocId"] = QJsonValue::fromVariant(QVariant(locId));
    v["curAvailableTime"] = m_curAvailEdit->dateTime().toString(Qt::ISODate);
    v["toolType"] = m_toolTypeEdit->text();
    v["maxWeight"] = m_maxWeightEdit->value();
    v["maxVolume"] = m_maxVolumeEdit->value();
    v["speed"] = m_speedEdit->value();
    v["shiftStart"] = m_shiftStartEdit->dateTime().toString(Qt::ISODate);
    v["shiftEnd"] = m_shiftEndEdit->dateTime().toString(Qt::ISODate);
    v["earnedRevenue"] = m_earnedRevenueEdit->value();
    v["status"] = "IDLE";

    m_api->createVehicle(v, [this](QJsonObject) { refresh(); });
    m_personIdEdit->clear();
    m_curLocEdit->clear();
    m_typeCombo->setCurrentIndex(0);
}

void VehiclesTab::onOffline() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    QStringList ids;
    for (const auto &idx : selected)
        ids << QString::number(m_vehicles[idx.row()].id);
    auto ret = QMessageBox::question(this, "确认下线",
        QString("确定将 %1 辆车下线吗？\nID: %2").arg(ids.size()).arg(ids.join(", ")));
    if (ret != QMessageBox::Yes) return;
    m_offlineBtn->setEnabled(false);
    auto *counter = new int(selected.size());
    for (const auto &idx : selected) {
        qulonglong id = m_vehicles[idx.row()].id;
        m_api->offlineVehicle(id, [this, counter](QJsonObject) {
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}

void VehiclesTab::onSetStatus() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    QString status = m_statusCombo->currentText();
    auto ret = QMessageBox::question(this, "确认设置状态",
        QString("确定将 %1 辆车的状态设为 %2 吗？").arg(selected.size()).arg(status));
    if (ret != QMessageBox::Yes) return;
    m_setStatusBtn->setEnabled(false);
    auto *counter = new int(selected.size());
    for (const auto &idx : selected) {
        qulonglong id = m_vehicles[idx.row()].id;
        m_api->setVehicleStatus(id, status, [this, counter](QJsonObject) {
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}

void VehiclesTab::onDelete() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    QStringList ids;
    for (const auto &idx : selected)
        ids << QString::number(m_vehicles[idx.row()].id);
    auto ret = QMessageBox::question(this, "确认删除",
        QString("确定删除 %1 辆车吗？相关路线也会被删除。\nID: %2").arg(ids.size()).arg(ids.join(", ")));
    if (ret != QMessageBox::Yes) return;
    m_deleteBtn->setEnabled(false);
    auto *counter = new int(selected.size());
    for (const auto &idx : selected) {
        qulonglong id = m_vehicles[idx.row()].id;
        m_api->deleteVehicle(id, [this, counter]() {
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}

void VehiclesTab::onSelectionChanged() {
    bool hasSelection = !m_table->selectionModel()->selectedRows().isEmpty();
    m_offlineBtn->setEnabled(hasSelection);
    m_setStatusBtn->setEnabled(hasSelection);
    m_deleteBtn->setEnabled(hasSelection);
}
