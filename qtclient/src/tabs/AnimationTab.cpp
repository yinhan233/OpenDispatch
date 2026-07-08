#include "AnimationTab.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QMessageBox>
#include <QJsonArray>
#include <QJsonObject>
#include <QJsonValue>
#include <QLabel>
#include "../Theme.h"

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

AnimationTab::AnimationTab(ApiClient *api, QWidget *parent)
    : QWidget(parent), m_api(api) {
    auto *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(20, 18, 20, 18);
    mainLayout->setSpacing(14);

    // ----- 页头 -----
    auto *headerLayout = new QHBoxLayout;
    headerLayout->setSpacing(12);
    auto *title = new QLabel("动画演示");
    title->setProperty("cssClass", "title");
    headerLayout->addWidget(title);
    headerLayout->addStretch();
    mainLayout->addLayout(headerLayout);

    // ----- 加载按钮 (独立行，醒目) -----
    auto *loadRow = new QHBoxLayout;
    loadRow->setSpacing(8);
    m_loadBtn = new QPushButton("加载调度数据");
    m_loadBtn->setCursor(Qt::PointingHandCursor);
    m_loadBtn->setMinimumHeight(36);
    m_loadBtn->setStyleSheet(
        "QPushButton { background: #1C1917; color: #FFFFFF; border: none; "
        "border-radius: 6px; padding: 8px 24px; font-size: 14px; font-weight: 600; }"
        "QPushButton:hover { background: #3F3F46; }"
        "QPushButton:pressed { background: #0C0A09; }"
        "QPushButton:disabled { background: #E7E5E0; color: #A8A29E; }");
    loadRow->addWidget(m_loadBtn);
    loadRow->addStretch();
    mainLayout->addLayout(loadRow);

    // ----- 控制卡片 -----
    auto *ctrlGroup = new QGroupBox("  播放控制  ");
    auto *ctrlLayout = new QHBoxLayout(ctrlGroup);
    ctrlLayout->setSpacing(8);
    ctrlLayout->setContentsMargins(8, 12, 8, 8);

    m_playBtn = new QPushButton("播放");
    m_playBtn->setStyleSheet(btnSuccess());
    m_pauseBtn = new QPushButton("暂停");
    m_pauseBtn->setStyleSheet(btnSecondary());
    m_resetBtn = new QPushButton("重置");
    m_resetBtn->setStyleSheet(btnSecondary());

    ctrlLayout->addWidget(m_playBtn);
    ctrlLayout->addWidget(m_pauseBtn);
    ctrlLayout->addWidget(m_resetBtn);

    ctrlLayout->addSpacing(16);
    auto *speedLabel = new QLabel("速度");
    speedLabel->setProperty("cssClass", "sectionLabel");
    ctrlLayout->addWidget(speedLabel);
    m_speedCombo = new QComboBox;
    m_speedCombo->addItem("1x", 1.0);
    m_speedCombo->addItem("2x", 2.0);
    m_speedCombo->addItem("5x", 5.0);
    m_speedCombo->addItem("10x", 10.0);
    m_speedCombo->addItem("30x", 30.0);
    m_speedCombo->setCurrentIndex(2);
    m_speedCombo->setMaximumWidth(90);
    ctrlLayout->addWidget(m_speedCombo);

    ctrlLayout->addSpacing(16);
    m_timeLabel = new QLabel("模拟时间: --");
    m_timeLabel->setStyleSheet(
        "font-weight: 600; color: #1C1917; background: #F5F4F1; "
        "border: 1px solid #E7E5E0; border-radius: 6px; padding: 6px 12px;");
    ctrlLayout->addWidget(m_timeLabel);

    ctrlLayout->addStretch();
    m_infoLabel = new QLabel("请先点击 [加载调度数据]");
    m_infoLabel->setStyleSheet("color: #A8A29E; font-size: 12px;");
    ctrlLayout->addWidget(m_infoLabel);

    mainLayout->addWidget(ctrlGroup);

    // ----- 进度条 -----
    auto *progLayout = new QHBoxLayout;
    progLayout->setSpacing(10);
    auto *progLabel = new QLabel("进度");
    progLabel->setProperty("cssClass", "sectionLabel");
    progLayout->addWidget(progLabel);
    m_progressSlider = new QSlider(Qt::Horizontal);
    m_progressSlider->setRange(0, 100);
    m_progressSlider->setValue(0);
    progLayout->addWidget(m_progressSlider, 1);
    mainLayout->addLayout(progLayout);

    // ----- 地图 -----
    auto *mapLabel = new QLabel("车辆位置图");
    mapLabel->setProperty("cssClass", "sectionLabel");
    mainLayout->addWidget(mapLabel);

    m_map = new MapWidget;
    m_map->setStyleSheet(
        "MapWidget { border: 1px solid #E7E5E0; border-radius: 10px; background: #FFFFFF; }");
    mainLayout->addWidget(m_map, 1);

    connect(m_loadBtn, &QPushButton::clicked, this, &AnimationTab::onLoadData);
    connect(m_playBtn, &QPushButton::clicked, this, &AnimationTab::onPlay);
    connect(m_pauseBtn, &QPushButton::clicked, this, &AnimationTab::onPause);
    connect(m_resetBtn, &QPushButton::clicked, this, &AnimationTab::onReset);
    connect(m_speedCombo, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &AnimationTab::onSpeedChanged);
    connect(m_progressSlider, &QSlider::sliderPressed, [this]() { m_sliderHeld = true; });
    connect(m_progressSlider, &QSlider::sliderReleased, [this]() { m_sliderHeld = false; });
    connect(m_progressSlider, &QSlider::valueChanged, [this](int val) {
        Q_UNUSED(val);
    });
    connect(m_map, &MapWidget::timeChanged, this, &AnimationTab::onTimeChanged);
    connect(m_map, &MapWidget::progressChanged, this, &AnimationTab::onProgressChanged);

    onSpeedChanged(m_speedCombo->currentIndex());
}

void AnimationTab::refresh() {
}

void AnimationTab::onLoadData() {
    m_loadBtn->setEnabled(false);
    m_infoLabel->setText("正在加载数据...");
    m_infoLabel->setStyleSheet("color: #B45309; font-size: 12px;");

    auto *state = new AnimLoadState();
    state->done = 0;

    auto checkDone = [this, state]() {
        state->done++;
        if (state->done >= state->total) {
            if (!state->error.isEmpty()) {
                m_infoLabel->setText("加载失败: " + state->error);
                m_infoLabel->setStyleSheet("color: #9F2F2D; font-size: 12px;");
            } else {
                m_map->setData(state->locs, state->orders, state->vehicles, state->routes);
                m_dataLoaded = true;
                m_infoLabel->setText(QString("已加载: %1 地点 · %2 订单 · %3 车辆 · %4 路线")
                    .arg(state->locs.size()).arg(state->orders.size())
                    .arg(state->vehicles.size()).arg(state->routes.size()));
                m_infoLabel->setStyleSheet("color: #15803D; font-size: 12px; font-weight: 500;");
            }
            m_loadBtn->setEnabled(true);
            delete state;
        }
    };

    m_api->getLocations([state, checkDone](QList<QJsonObject> list) {
        for (const auto &o : list) {
            AnimLocation l;
            l.id = o["id"].toVariant().toULongLong();
            l.name = o["name"].toString();
            l.lng = o["lng"].toDouble();
            l.lat = o["lat"].toDouble();
            state->locs.append(l);
        }
        checkDone();
    });

    m_api->getOrders([state, checkDone](QList<QJsonObject> list) {
        for (const auto &o : list) {
            AnimOrder ord;
            ord.id = o["id"].toVariant().toULongLong();
            ord.pickupLocId = o["pickupLocId"].toVariant().toULongLong();
            ord.deliveryLocId = o["deliveryLocId"].toVariant().toULongLong();
            ord.serviceTime = o["serviceTime"].toInt();
            state->orders.append(ord);
        }
        checkDone();
    });

    m_api->getVehicles([state, checkDone](QList<QJsonObject> list) {
        for (const auto &o : list) {
            AnimVehicle v;
            v.id = o["id"].toVariant().toULongLong();
            v.toolType = o["toolType"].toString();
            v.status = o["status"].toString();
            v.curLocId = o["curLocId"].toVariant().toULongLong();
            v.speed = o["speed"].toDouble();
            state->vehicles.append(v);
        }
        checkDone();
    });

    m_api->currentSchedule([state, checkDone](QJsonObject obj) {
        ScheduleResult sr = ScheduleResult::fromJson(obj);
        for (const auto &vr : sr.vehicles) {
            AnimVehicleRoute avr;
            avr.vehicleId = vr.vehicleId;
            for (const auto &leg : vr.route) {
                AnimRouteLeg aleg;
                aleg.orderId = leg.orderId;
                aleg.plannedStart = leg.plannedStart;
                aleg.plannedEnd = leg.plannedEnd;
                aleg.idleBefore = leg.idleBefore;
                avr.route.append(aleg);
            }
            if (!avr.route.isEmpty()) state->routes.append(avr);
        }
        checkDone();
    });
}

void AnimationTab::onPlay() {
    if (!m_dataLoaded) {
        QMessageBox::information(this, "提示", "请先点击「加载调度数据」");
        return;
    }
    m_map->play();
}

void AnimationTab::onPause() {
    m_map->pause();
}

void AnimationTab::onReset() {
    m_map->reset();
}

void AnimationTab::onSpeedChanged(int idx) {
    double factor = m_speedCombo->itemData(idx).toDouble();
    m_map->setSpeed(factor);
}

void AnimationTab::onTimeChanged(const QDateTime &t) {
    m_timeLabel->setText("模拟时间: " + t.toString("yyyy-MM-dd hh:mm:ss"));
}

void AnimationTab::onProgressChanged(int pct) {
    if (!m_sliderHeld) {
        QSignalBlocker b(m_progressSlider);
        m_progressSlider->setValue(pct);
    }
}
