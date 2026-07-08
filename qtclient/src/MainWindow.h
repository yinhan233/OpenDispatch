#pragma once

#include <QMainWindow>
#include <QTabWidget>
#include <QLabel>
#include "ApiClient.h"

class OrdersTab;
class VehiclesTab;
class LocationsTab;
class ScheduleTab;
class AnimationTab;
class MapKeyDialog;

class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);

private slots:
    void openMapKeySettings();
    void onTabChanged(int idx);

private:
    ApiClient *m_api;
    QTabWidget *m_tabs;
    OrdersTab *m_ordersTab;
    VehiclesTab *m_vehiclesTab;
    LocationsTab *m_locationsTab;
    ScheduleTab *m_scheduleTab;
    AnimationTab *m_animationTab;
    QLabel *m_headerTitle;
};
