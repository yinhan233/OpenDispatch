#include "MapKeyDialog.h"
#include "ApiClient.h"
#include <QMessageBox>
#include <QHBoxLayout>
#include <QLabel>
#include <QFrame>

MapKeyDialog::MapKeyDialog(ApiClient *api, QWidget *parent)
    : QDialog(parent), m_api(api) {
    setWindowTitle("腾讯地图 Key 配置");
    setMinimumWidth(520);
    setStyleSheet(
        "QDialog { background: #FAFAF9; }"
        "QLabel { background: transparent; border: none; }");

    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(28, 24, 28, 24);
    layout->setSpacing(16);

    //Title
    auto *titleLabel = new QLabel("腾讯地图 Key 配置");
    titleLabel->setStyleSheet("font-size: 18px; font-weight: 700; color: #1C1917; letter-spacing: -0.3px;");
    layout->addWidget(titleLabel);

    auto *infoLabel = new QLabel(
        "配置腾讯位置服务的 Key 和 Secret Key，用于地址解析（地理编码）。\n"
        "配置保存到后端服务器的 ~/.logistics_manager/mapkey.json 文件。");
    infoLabel->setWordWrap(true);
    infoLabel->setStyleSheet("color: #57534E; font-size: 12px; line-height: 1.5;");
    layout->addWidget(infoLabel);

    // 获取地址
    auto *linkLabel = new QLabel("获取地址: https://lbs.qq.com/dev/console/key/manage");
    linkLabel->setStyleSheet("color: #1F6C9F; font-size: 12px; font-weight: 500;");
    layout->addWidget(linkLabel);

    // 分隔线
    auto *sep = new QFrame;
    sep->setFrameShape(QFrame::HLine);
    sep->setStyleSheet("color: #E7E5E0; background: #E7E5E0; max-height: 1px;");
    layout->addWidget(sep);

    // 表单
    auto *form = new QFormLayout;
    form->setSpacing(12);
    form->setLabelAlignment(Qt::AlignRight | Qt::AlignVCenter);

    m_keyEdit = new QLineEdit;
    m_keyEdit->setPlaceholderText("如: ZMQBZ-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX");
    m_skEdit = new QLineEdit;
    m_skEdit->setPlaceholderText("如: BvLRXTeCmzZZNDp3aufiCGgk2Wsp08eD");
    m_skEdit->setEchoMode(QLineEdit::Password);

    form->addRow("Key", m_keyEdit);
    form->addRow("Secret Key", m_skEdit);
    layout->addLayout(form);

    // 状态
    m_statusLabel = new QLabel("正在加载配置...");
    m_statusLabel->setStyleSheet("color: #57534E; font-size: 12px; font-weight: 500;");
    layout->addWidget(m_statusLabel);

    // 路径
    m_pathLabel = new QLabel;
    m_pathLabel->setStyleSheet("color: #A8A29E; font-size: 11px;");
    m_pathLabel->setWordWrap(true);
    layout->addWidget(m_pathLabel);

    // 按钮
    auto *btnRow = new QHBoxLayout;
    btnRow->setSpacing(8);
    m_saveBtn = new QPushButton("保存");
    auto *closeBtn = new QPushButton("取消");
    closeBtn->setProperty("cssClass", "secondary");
    btnRow->addStretch();
    btnRow->addWidget(closeBtn);
    btnRow->addWidget(m_saveBtn);
    layout->addLayout(btnRow);

    connect(m_saveBtn, &QPushButton::clicked, this, &MapKeyDialog::onSave);
    connect(closeBtn, &QPushButton::clicked, this, &QDialog::reject);

    onLoad();
}

void MapKeyDialog::onLoad() {
    m_api->getMapKeyConfig([this](QJsonObject obj) {
        if (obj.contains("configured")) {
            bool configured = obj["configured"].toBool();
            QString maskedKey = obj["key"].toString();
            if (configured) {
                m_statusLabel->setText(QString("  ✓  已配置  ·  Key: %1  ").arg(maskedKey));
                m_statusLabel->setStyleSheet(
                    "color: #15803D; font-size: 12px; font-weight: 600; "
                    "background: #EDF3EC; border: 1px solid #D1E7D0; "
                    "border-radius: 10px; padding: 5px 14px;");
                m_keyEdit->setPlaceholderText("已配置，留空保持不变；如需更换请输入新 Key");
                m_skEdit->setPlaceholderText("已配置，留空保持不变；如需更换请输入新 SK");
            } else {
                m_statusLabel->setText("  ✕  未配置  ");
                m_statusLabel->setStyleSheet(
                    "color: #9F2F2D; font-size: 12px; font-weight: 600; "
                    "background: #FDEBEC; border: 1px solid #FECACA; "
                    "border-radius: 10px; padding: 5px 14px;");
                m_keyEdit->setPlaceholderText("请输入 Key");
                m_skEdit->setPlaceholderText("请输入 Secret Key");
            }
            m_pathLabel->setText("配置文件路径: " + obj["path"].toString());
        } else {
            m_statusLabel->setText("  !  无法连接服务器  ");
            m_statusLabel->setStyleSheet(
                "color: #9F2F2D; font-size: 12px; font-weight: 600; "
                "background: #FDEBEC; border: 1px solid #FECACA; "
                "border-radius: 10px; padding: 5px 14px;");
        }
    });
}

void MapKeyDialog::onSave() {
    QString key = m_keyEdit->text().trimmed();
    QString sk = m_skEdit->text().trimmed();
    if (key.isEmpty() && sk.isEmpty()) {
        QMessageBox::warning(this, "输入为空",
            "请输入 Key 和 Secret Key。\n"
            "如果只想查看当前状态，请点\"取消\"。");
        return;
    }
    if (key.isEmpty()) {
        QMessageBox::warning(this, "输入错误", "Key 不能为空。");
        return;
    }
    m_saveBtn->setEnabled(false);
    m_saveBtn->setText("保存中...");
    m_api->saveMapKeyConfig(key, sk, [this](QJsonObject obj) {
        m_saveBtn->setEnabled(true);
        m_saveBtn->setText("保存");
        if (obj.contains("ok") && obj["ok"].toBool()) {
            QMessageBox::information(this, "保存成功",
                "Key/SK 已保存并立即生效。\n配置文件: " + obj["path"].toString());
            accept();
        } else {
            QMessageBox::warning(this, "保存失败",
                "保存配置失败，请检查后端服务是否正常。");
        }
    });
}
