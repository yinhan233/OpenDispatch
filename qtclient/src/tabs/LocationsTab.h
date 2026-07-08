#pragma once

#include <QWidget>
#include <QTableWidget>
#include <QPushButton>
#include <QLineEdit>
#include <QDoubleSpinBox>
#include <QElapsedTimer>
#include "../ApiClient.h"
#include "../Models.h"

class LocationsTab : public QWidget {
    Q_OBJECT
public:
    explicit LocationsTab(ApiClient *api, QWidget *parent = nullptr);

public slots:
    void refresh();

private slots:
    void onAdd();
    void onGeocode();
    void onDelete();
    void onSelectionChanged();

private:
    ApiClient *m_api;
    QTableWidget *m_table;
    QPushButton *m_addBtn;
    QPushButton *m_refreshBtn;
    QPushButton *m_geocodeBtn;
    QPushButton *m_deleteBtn;
    QLineEdit *m_nameEdit;
    QLineEdit *m_addrEdit;
    QDoubleSpinBox *m_lngEdit;
    QDoubleSpinBox *m_latEdit;

    QElapsedTimer m_lastGeocodeTime;
    QString m_lastGeocodeAddress;

    QList<LocationData> m_locations;
};
