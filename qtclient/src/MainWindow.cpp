#include "MainWindow.h"
#include "MapKeyDialog.h"
#include "tabs/OrdersTab.h"
#include "tabs/VehiclesTab.h"
#include "tabs/LocationsTab.h"
#include "tabs/ScheduleTab.h"
#include "tabs/AnimationTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFrame>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent), m_api(new ApiClient(this)) {
    setWindowTitle("物流车辆调度系统");
    resize(1280, 820);
    setMinimumSize(1000, 680);

    //中间容器
    auto *central = new QWidget(this);
    auto *centralLayout = new QVBoxLayout(central);
    centralLayout->setContentsMargins(0, 0, 0, 0);
    centralLayout->setSpacing(0);

    //头部栏
    auto *header = new QFrame;
    header->setObjectName("appHeader");
    header->setStyleSheet(
        "QFrame#appHeader { background: #FFFFFF; border-bottom: 1px solid #E7E5E0; }"
        "QLabel { background: transparent; border: none; }");
    header->setFixedHeight(64);
    auto *headerLayout = new QHBoxLayout(header);
    headerLayout->setContentsMargins(28, 0, 28, 0);
    headerLayout->setSpacing(14);

    m_headerTitle = new QLabel("物流车辆调度系统");
    m_headerTitle->setStyleSheet(
        "font-size: 18px; font-weight: 700; color: #1C1917; letter-spacing: -0.3px;");
    headerLayout->addWidget(m_headerTitle);

    headerLayout->addStretch();

    auto *settingsBtn = new QPushButton("设置");
    settingsBtn->setProperty("cssClass", "secondary");
    settingsBtn->setCursor(Qt::PointingHandCursor);
    settingsBtn->setToolTip("配置腾讯地图 API Key 和 Secret Key");
    headerLayout->addWidget(settingsBtn);
    connect(settingsBtn, &QPushButton::clicked, this, &MainWindow::openMapKeySettings);

    centralLayout->addWidget(header);

    //标签页
    auto *tabContainer = new QWidget;
    tabContainer->setStyleSheet("background: #FAFAF9;");
    auto *tabLayout = new QVBoxLayout(tabContainer);
    tabLayout->setContentsMargins(24, 14, 24, 14);
    tabLayout->setSpacing(0);

    m_tabs = new QTabWidget;
    m_tabs->setDocumentMode(true);
    m_ordersTab = new OrdersTab(m_api);
    m_vehiclesTab = new VehiclesTab(m_api);
    m_locationsTab = new LocationsTab(m_api);
    m_scheduleTab = new ScheduleTab(m_api);
    m_animationTab = new AnimationTab(m_api);

    m_tabs->addTab(m_ordersTab, "  订单管理  ");
    m_tabs->addTab(m_vehiclesTab, "  车辆管理  ");
    m_tabs->addTab(m_locationsTab, "  地点管理  ");
    m_tabs->addTab(m_scheduleTab, "  调度方案  ");
    m_tabs->addTab(m_animationTab, "  动画演示  ");

    tabLayout->addWidget(m_tabs);
    centralLayout->addWidget(tabContainer, 1);

    setCentralWidget(central);

    connect(m_tabs, &QTabWidget::currentChanged, this, &MainWindow::onTabChanged);
}

void MainWindow::openMapKeySettings() {
    MapKeyDialog dlg(m_api, this);
    dlg.exec();
}

void MainWindow::onTabChanged(int idx) {
    Q_UNUSED(idx);
    if (m_tabs->currentWidget() == m_animationTab) {
        m_animationTab->refresh();
    }
}
