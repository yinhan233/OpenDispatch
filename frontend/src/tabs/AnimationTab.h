#pragma once

#include <QWidget>
#include <QPushButton>
#include <QLabel>
#include <QSlider>
#include <QComboBox>
#include "../ApiClient.h"
#include "../Models.h"
#include "../widgets/MapWidget.h"

struct AnimLoadState {
    QList<AnimLocation> locs;
    QList<AnimOrder> orders;
    QList<AnimVehicle> vehicles;
    QList<AnimVehicleRoute> routes;
    int done = 0;
    int total = 4;
    QString error;
};

class AnimationTab : public QWidget {
    Q_OBJECT
public:
    explicit AnimationTab(ApiClient *api, QWidget *parent = nullptr);

public slots:
    void refresh();

private slots:
    void onLoadData();
    void onPlay();
    void onPause();
    void onReset();
    void onSpeedChanged(int idx);
    void onTimeChanged(const QDateTime &t);
    void onProgressChanged(int pct);

private:
    ApiClient *m_api;
    MapWidget *m_map;
    QPushButton *m_loadBtn;
    QPushButton *m_playBtn;
    QPushButton *m_pauseBtn;
    QPushButton *m_resetBtn;
    QComboBox *m_speedCombo;
    QSlider *m_progressSlider;
    QLabel *m_timeLabel;
    QLabel *m_infoLabel;
    bool m_dataLoaded = false;
    bool m_sliderHeld = false;
};
