#pragma once

#include <QWidget>
#include <QTableWidget>
#include <QPushButton>
#include <QLineEdit>
#include <QSpinBox>
#include <QDoubleSpinBox>
#include <QDateTimeEdit>
#include <QComboBox>
#include <QLabel>
#include "../ApiClient.h"
#include "../Models.h"

class OrdersTab : public QWidget {
    Q_OBJECT
public:
    explicit OrdersTab(ApiClient *api, QWidget *parent = nullptr);

public slots:
    void refresh();

private slots:
    void onAdd();
    void onDelete();
    void onSelectionChanged();
    void onSetStatus();

private:
    ApiClient *m_api;
    QTableWidget *m_table;
    QPushButton *m_addBtn;
    QPushButton *m_deleteBtn;
    QPushButton *m_refreshBtn;
    QPushButton *m_setStatusBtn;
    QComboBox *m_statusCombo;

    // Form fields
    QLineEdit *m_pickupLocEdit;
    QLineEdit *m_deliveryLocEdit;
    QDateTimeEdit *m_timeStartEdit;
    QDateTimeEdit *m_timeEndEdit;
    QDoubleSpinBox *m_revenueEdit;
    QDoubleSpinBox *m_weightEdit;
    QDoubleSpinBox *m_volumeEdit;
    QSpinBox *m_serviceEdit;

    QList<OrderData> m_orders;
};
