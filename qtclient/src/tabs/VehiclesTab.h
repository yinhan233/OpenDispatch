#pragma once

#include <QWidget>
#include <QTableWidget>
#include <QPushButton>
#include <QLineEdit>
#include <QDoubleSpinBox>
#include <QDateTimeEdit>
#include <QComboBox>
#include "../ApiClient.h"
#include "../Models.h"

class VehiclesTab : public QWidget {
    Q_OBJECT
public:
    explicit VehiclesTab(ApiClient *api, QWidget *parent = nullptr);

public slots:
    void refresh();

private slots:
    void onAdd();
    void onOffline();
    void onSetStatus();
    void onDelete();
    void onSelectionChanged();
    void onTypeChanged(int index);

private:
    ApiClient *m_api;
    QTableWidget *m_table;
    QPushButton *m_addBtn;
    QPushButton *m_offlineBtn;
    QPushButton *m_setStatusBtn;
    QPushButton *m_deleteBtn;
    QPushButton *m_refreshBtn;
    QComboBox *m_statusCombo;

    QComboBox *m_typeCombo;
    QLineEdit *m_personIdEdit;
    QLineEdit *m_curLocEdit;
    QDateTimeEdit *m_curAvailEdit;
    QLineEdit *m_toolTypeEdit;
    QDoubleSpinBox *m_maxWeightEdit;
    QDoubleSpinBox *m_maxVolumeEdit;
    QDoubleSpinBox *m_speedEdit;
    QDateTimeEdit *m_shiftStartEdit;
    QDateTimeEdit *m_shiftEndEdit;
    QDoubleSpinBox *m_earnedRevenueEdit;

    QList<VehicleData> m_vehicles;
    QList<VehicleTypeData> m_types;

    void loadTypes();
};
