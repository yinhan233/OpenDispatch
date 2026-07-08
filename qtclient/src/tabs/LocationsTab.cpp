#include "LocationsTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QHeaderView>
#include <QMessageBox>
#include <QLabel>
#include <QGridLayout>

static QString btnPrimary() {
    return "QPushButton { background: #1C1917; color: #FFFFFF; border: none; "
           "border-radius: 6px; padding: 8px 18px; font-size: 13px; font-weight: 500; min-height: 20px; }"
           "QPushButton:hover { background: #3F3F46; }"
           "QPushButton:pressed { background: #0C0A09; }"
           "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }";
}
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

LocationsTab::LocationsTab(ApiClient *api, QWidget *parent)
    : QWidget(parent), m_api(api) {
    m_lastGeocodeTime.start();
    auto *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(20, 18, 20, 18);
    mainLayout->setSpacing(14);

    // ----- 页头 -----
    auto *headerLayout = new QHBoxLayout;
    headerLayout->setSpacing(12);
    auto *title = new QLabel("地点管理");
    title->setProperty("cssClass", "title");
    headerLayout->addWidget(title);
    headerLayout->addStretch();
    mainLayout->addLayout(headerLayout);

    // ----- 表单卡片 (双列) -----
    auto *formGroup = new QGroupBox("  新增地点  ");

    m_nameEdit = new QLineEdit;
    m_nameEdit->setPlaceholderText("地点名称");
    m_lngEdit = new QDoubleSpinBox;
    m_lngEdit->setRange(-180.0, 180.0);
    m_lngEdit->setDecimals(6);
    m_latEdit = new QDoubleSpinBox;
    m_latEdit->setRange(-90.0, 90.0);
    m_latEdit->setDecimals(6);
    m_addrEdit = new QLineEdit;
    m_addrEdit->setPlaceholderText("输入地址, 如: 北京市海淀区...");

    auto *grid = new QGridLayout;
    grid->setSpacing(10);
    auto addField = [&](int row, int col, const QString &label, QWidget *w) {
        auto *l = new QLabel(label);
        l->setAlignment(Qt::AlignRight | Qt::AlignVCenter);
        l->setStyleSheet("color: #57534E; font-size: 12px; font-weight: 500;");
        grid->addWidget(l, row, col * 2);
        grid->addWidget(w, row, col * 2 + 1);
    };
    addField(0, 0, "名称", m_nameEdit);
    addField(0, 1, "地址", m_addrEdit);
    addField(1, 0, "经度", m_lngEdit);
    addField(1, 1, "纬度", m_latEdit);
    grid->setColumnStretch(1, 1);
    grid->setColumnStretch(3, 1);
    grid->setColumnStretch(5, 1);

    m_geocodeBtn = new QPushButton("地址解析");
    m_geocodeBtn->setStyleSheet(btnSecondary());
    m_addBtn = new QPushButton("添加地点");
    m_addBtn->setStyleSheet(btnPrimary());
    auto *btnRow = new QHBoxLayout;
    btnRow->setSpacing(8);
    btnRow->addStretch();
    btnRow->addWidget(m_geocodeBtn);
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

    auto *sectionLabel = new QLabel("地点列表");
    sectionLabel->setProperty("cssClass", "sectionLabel");
    tableToolbar->addWidget(sectionLabel);
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
    m_table->setColumnCount(4);
    m_table->setHorizontalHeaderLabels({"ID", "名称", "经度", "纬度"});
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_table->setAlternatingRowColors(true);
    m_table->verticalHeader()->setVisible(false);
    m_table->setShowGrid(false);
    m_table->verticalHeader()->setDefaultSectionSize(38);
    m_table->setStyleSheet("QTableWidget { border: 1px solid #E7E5E0; border-radius: 8px; }");
    m_table->horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);

    mainLayout->addWidget(m_table, 1);

    connect(m_addBtn, &QPushButton::clicked, this, &LocationsTab::onAdd);
    connect(m_geocodeBtn, &QPushButton::clicked, this, &LocationsTab::onGeocode);
    connect(m_refreshBtn, &QPushButton::clicked, this, &LocationsTab::refresh);
    connect(m_deleteBtn, &QPushButton::clicked, this, &LocationsTab::onDelete);
    connect(m_table->selectionModel(), &QItemSelectionModel::selectionChanged,
            this, &LocationsTab::onSelectionChanged);

    refresh();
}

void LocationsTab::refresh() {
    m_api->getLocations([this](QList<QJsonObject> list) {
        m_locations.clear();
        for (const auto &l : list) m_locations.append(LocationData::fromJson(l));
        m_table->setRowCount(m_locations.size());
        for (int i = 0; i < m_locations.size(); ++i) {
            const auto &l = m_locations[i];
            m_table->setItem(i, 0, new QTableWidgetItem(QString::number(l.id)));
            m_table->setItem(i, 1, new QTableWidgetItem(l.name));
            m_table->setItem(i, 2, new QTableWidgetItem(QString::number(l.lng, 'f', 4)));
            m_table->setItem(i, 3, new QTableWidgetItem(QString::number(l.lat, 'f', 4)));
        }
    });
}

void LocationsTab::onAdd() {
    QString name = m_nameEdit->text().trimmed();
    if (name.isEmpty()) {
        QMessageBox::warning(this, "名称为空", "请输入地点名称。");
        m_nameEdit->setFocus();
        return;
    }
    QJsonObject l;
    l["name"] = name;
    l["lng"] = m_lngEdit->value();
    l["lat"] = m_latEdit->value();
    m_api->createLocation(l, [this](QJsonObject) {
        refresh();
        m_nameEdit->clear();
        m_addrEdit->clear();
    });
}

void LocationsTab::onGeocode() {
    QString address = m_addrEdit->text().trimmed();
    if (address.isEmpty()) return;

    if (m_lastGeocodeTime.elapsed() < 2000) {
        QMessageBox::information(this, "请稍候", "地址解析请求过于频繁，请2秒后再试。");
        return;
    }
    if (address == m_lastGeocodeAddress && m_lastGeocodeTime.elapsed() < 60000) {
        QMessageBox::information(this, "已解析", "该地址刚刚已解析过，请勿重复请求（1分钟内）。");
        return;
    }

    m_lastGeocodeTime.restart();
    m_lastGeocodeAddress = address;
    m_geocodeBtn->setEnabled(false);
    m_api->geocode(address, [this](QJsonObject res) {
        m_geocodeBtn->setEnabled(true);
        if (res.contains("lng")) {
            m_lngEdit->setValue(res["lng"].toDouble());
            m_latEdit->setValue(res["lat"].toDouble());
        } else {
            QMessageBox::warning(this, "地址解析失败", "无法解析该地址，请检查地址是否正确或稍后重试。\n（腾讯地图API每日配额有限，请谨慎使用）");
        }
    });
}

void LocationsTab::onDelete() {
    auto selected = m_table->selectionModel()->selectedRows();
    if (selected.isEmpty()) return;
    // Check for protected location (ID=0)
    for (const auto &idx : selected) {
        if (m_locations[idx.row()].id == 0) {
            QMessageBox::warning(this, "无法删除",
                "默认地点(ID=0)是系统保留地点，不能删除。");
            return;
        }
    }
    QStringList names;
    for (const auto &idx : selected)
        names << m_locations[idx.row()].name;
    auto ret = QMessageBox::question(this, "确认删除",
        QString("确定删除 %1 个地点吗？\n\n"
                "此操作将级联删除：\n"
                "  • 引用此地点的所有订单及路线明细\n"
                "  • 此地点相关的距离缓存\n\n"
                "名称: %2\n\n"
                "此操作不可撤销。")
            .arg(selected.size()).arg(names.join(", ")));
    if (ret != QMessageBox::Yes) return;
    m_deleteBtn->setEnabled(false);
    auto *counter = new int(selected.size());
    for (const auto &idx : selected) {
        qulonglong id = m_locations[idx.row()].id;
        m_api->deleteLocation(id, [this, counter](bool, const QString&) {
            if (--(*counter) == 0) {
                delete counter;
                refresh();
            }
        });
    }
}

void LocationsTab::onSelectionChanged() {
    m_deleteBtn->setEnabled(!m_table->selectionModel()->selectedRows().isEmpty());
}
