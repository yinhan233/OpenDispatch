#pragma once

#include <QWidget>
#include <QPushButton>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include "../ApiClient.h"
#include "../Models.h"
#include "../widgets/GanttChart.h"

class ScheduleTab : public QWidget {
    Q_OBJECT
public:
    explicit ScheduleTab(ApiClient *api, QWidget *parent = nullptr);

public slots:
    void refresh();

private slots:
    void onRun();
    void onDryRun();
    void onReschedule();
    void onDryRunDynamic();
    void onComplete();
    void onCurrent();

private:
    ApiClient *m_api;
    GanttChart *m_gantt;
    QPushButton *m_runBtn;
    QPushButton *m_dryRunBtn;
    QPushButton *m_rescheduleBtn;
    QPushButton *m_dryRunDynBtn;
    QPushButton *m_completeBtn;
    QPushButton *m_currentBtn;
    QLabel *m_statsLabel;
    QWidget *m_idleMetric;
    QWidget *m_revenueMetric;
    QWidget *m_feasibleMetric;
    QWidget *m_vehiclesMetric;
    QWidget *m_unassignedMetric;
    QLineEdit *m_vehicleIdEdit;
    QLineEdit *m_orderIdEdit;
    QListWidget *m_unassignedList;
    ScheduleResult m_lastResult;

    void updateMetrics();
};
