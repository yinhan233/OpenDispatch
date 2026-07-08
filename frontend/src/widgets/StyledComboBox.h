#pragma once

#include <QComboBox>
#include <QAbstractItemView>
#include <QFrame>

class StyledComboBox : public QComboBox {
    Q_OBJECT
public:
    using QComboBox::QComboBox;

    void showPopup() override {
        QComboBox::showPopup();
        if (auto* v = view()) {
            v->setStyleSheet(
                "QAbstractItemView { border: 1px solid #D6D3CB; border-radius: 6px; "
                "background: #FFFFFF; padding: 4px; outline: none; }"
                "QAbstractItemView::item { padding: 6px 12px; min-height: 24px; border: none; border-radius: 4px; }"
                "QAbstractItemView::item:selected { background: #F5F4F1; }");
            if (auto* popup = v->window()) {
                popup->setStyleSheet("background: transparent; border: none;");
            }
        }
    }
};
