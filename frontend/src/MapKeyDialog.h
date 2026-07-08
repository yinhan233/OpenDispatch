#pragma once

#include <QDialog>
#include <QLineEdit>
#include <QLabel>
#include <QPushButton>
#include <QVBoxLayout>
#include <QFormLayout>

class ApiClient;

class MapKeyDialog : public QDialog {
    Q_OBJECT
public:
    explicit MapKeyDialog(ApiClient *api, QWidget *parent = nullptr);

private slots:
    void onLoad();
    void onSave();

private:
    ApiClient *m_api;
    QLineEdit *m_keyEdit;
    QLineEdit *m_skEdit;
    QLabel *m_statusLabel;
    QLabel *m_pathLabel;
    QPushButton *m_saveBtn;
};
