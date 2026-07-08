#pragma once

#include <QString>
#include <QColor>
#include <QFont>

namespace Theme {
namespace Palette {
    // 中性色
    inline QColor Canvas()       { return QColor("#FAFAF9"); }   // 应用背景
    inline QColor Surface()      { return QColor("#FFFFFF"); }   // 卡片表面
    inline QColor SurfaceAlt()   { return QColor("#F5F4F1"); }   // 次表面 / 条纹
    inline QColor Border()       { return QColor("#E7E5E0"); }   // 结构边框
    inline QColor BorderStrong() { return QColor("#D6D3CB"); }   // 强边框
    inline QColor TextPrimary()  { return QColor("#1C1917"); }   // 主文本 近黑
    inline QColor TextSecondary(){ return QColor("#57534E"); }   // 次文本
    inline QColor TextMuted()    { return QColor("#A8A29E"); }   // 弱文本

    // 语义强调
    inline QColor Accent()       { return QColor("#1C1917"); }   // 主操作 纯黑
    inline QColor AccentHover()  { return QColor("#3F3F46"); }   // 主操作悬停
    inline QColor Success()      { return QColor("#15803D"); }   // 成功 深绿
    inline QColor SuccessBg()    { return QColor("#EDF3EC"); }   // 成功底
    inline QColor Warning()      { return QColor("#B45309"); }   // 警告 暖橙
    inline QColor WarningBg()    { return QColor("#FBF3DB"); }   // 警告底
    inline QColor Danger()       { return QColor("#9F2F2D"); }   // 危险 暗红
    inline QColor DangerBg()     { return QColor("#FDEBEC"); }   // 危险底
    inline QColor Info()         { return QColor("#1F6C9F"); }   // 信息 暗蓝
    inline QColor InfoBg()       { return QColor("#E1F3FE"); }   // 信息底

    // 数据可视化
    inline QColor Data1()        { return QColor("#1C1917"); }
    inline QColor Data2()        { return QColor("#9F2F2D"); }
    inline QColor Data3()        { return QColor("#15803D"); }
    inline QColor Data4()        { return QColor("#B45309"); }
    inline QColor Data5()        { return QColor("#1F6C9F"); }
    inline QColor Data6()        { return QColor("#7C3AED"); }
    inline QColor Data7()        { return QColor("#0E7490"); }
    inline QColor Data8()        { return QColor("#BE185D"); }

    inline QColor orderColor(int idx) {
        static const QColor palette[] = {
            Data1(), Data2(), Data3(), Data4(),
            Data5(), Data6(), Data7(), Data8(),
        };
        return palette[idx % 8];
    }
}

// 保留向后兼容的 Colors 命名空间
namespace Colors {
    inline QColor Primary()      { return Palette::Accent(); }
    inline QColor PrimaryDark()  { return Palette::AccentHover(); }
    inline QColor Success()      { return Palette::Success(); }
    inline QColor Warning()      { return Palette::Warning(); }
    inline QColor Danger()       { return Palette::Danger(); }
    inline QColor Background()   { return Palette::Canvas(); }
    inline QColor CardBg()       { return Palette::Surface(); }
    inline QColor Border()       { return Palette::Border(); }
    inline QColor TextPrimary()  { return Palette::TextPrimary(); }
    inline QColor TextMuted()    { return Palette::TextMuted(); }
    inline QColor RowStripe()    { return Palette::SurfaceAlt(); }
    inline QColor StatsBg()      { return Palette::InfoBg(); }
    inline QColor StatsBorder()  { return QColor("#bae6fd"); }
    inline QColor IdleAnnot()    { return Palette::Danger(); }
    inline QColor Unassigned()   { return Palette::Danger(); }
    inline QColor orderColor(int idx) { return Palette::orderColor(idx); }
}

inline QString globalStyleSheet() {
#ifdef __EMSCRIPTEN__
    const QString fontFamily = QStringLiteral("'DejaVu Sans', sans-serif");
#else
    const QString fontFamily = QStringLiteral("'Microsoft YaHei', 'Noto Sans CJK SC', 'WenQuanYi Micro Hei', sans-serif");
#endif
    QString css = QStringLiteral(R"(
        * {
            font-family: %1;
        }

        QMainWindow {
            background-color: #FAFAF9;
        }

        QWidget {
            font-size: 13px;
        }

        QLabel {
            color: #1C1917;
        }

        /* ===== 标签页 ===== */
        QTabWidget::pane {
            border: none;
            background: #FAFAF9;
            top: -1px;
        }

        QTabBar {
            background: transparent;
        }

        QTabBar::tab {
            background: transparent;
            color: #A8A29E;
            border: none;
            border-bottom: 2px solid transparent;
            padding: 12px 22px;
            margin-right: 2px;
            font-size: 13px;
            font-weight: 500;
            min-width: 96px;
        }

        QTabBar::tab:selected {
            background: transparent;
            color: #1C1917;
            border-bottom: 2px solid #1C1917;
            font-weight: 600;
        }

        QTabBar::tab:hover:!selected {
            color: #57534E;
            border-bottom: 2px solid #D6D3CB;
            background: #F5F4F1;
            border-top-left-radius: 6px;
            border-top-right-radius: 6px;
        }

        /* ===== 分组框 ===== */
        QGroupBox {
            font-size: 15px;
            font-weight: 600;
            border: 1px solid #E7E5E0;
            border-radius: 8px;
            margin-top: 12px;
            padding: 14px 12px 10px 12px;
            background: transparent;
        }

        QGroupBox::title {
            subcontrol-origin: margin;
            left: 12px;
            padding: 0 8px;
            background: #FAFAF9;
            color: #57534E;
        }

        /* ===== 按钮 ===== */
        QPushButton {
            background: #1C1917;
            color: #FFFFFF;
            border: none;
            border-radius: 6px;
            padding: 8px 18px;
            font-size: 13px;
            font-weight: 500;
            min-height: 20px;
        }

        QPushButton:hover {
            background: #3F3F46;
        }

        QPushButton:pressed {
            background: #0C0A09;
        }

        QPushButton:disabled {
            background: #E7E5E0;
            color: #A8A29E;
        }

        QPushButton[cssClass="secondary"] {
            background: #F5F4F1;
            color: #1C1917;
            border: 1px solid #D6D3CB;
        }

        QPushButton[cssClass="secondary"]:hover {
            background: #ECEBE8;
            border-color: #A8A29E;
        }

        QPushButton[cssClass="secondary"]:pressed {
            background: #E7E5E0;
        }

        QPushButton[cssClass="danger"] {
            background: #9F2F2D;
            color: #FFFFFF;
        }

        QPushButton[cssClass="danger"]:hover {
            background: #8B2725;
        }

        QPushButton[cssClass="success"] {
            background: #15803D;
            color: #FFFFFF;
        }

        QPushButton[cssClass="success"]:hover {
            background: #166534;
        }

        QPushButton[cssClass="warning"] {
            background: #B45309;
            color: #FFFFFF;
        }

        QPushButton[cssClass="warning"]:hover {
            background: #92400E;
        }

        QPushButton[cssClass="ghost"] {
            background: transparent;
            color: #57534E;
            border: none;
            padding: 6px 12px;
        }

        QPushButton[cssClass="ghost"]:hover {
            color: #1C1917;
            background: #F5F4F1;
            border-radius: 4px;
        }

        /* ===== 输入控件 ===== */
        QLineEdit, QSpinBox, QDoubleSpinBox, QDateTimeEdit, QComboBox {
            background: #FFFFFF;
            border: 1px solid #D6D3CB;
            padding: 7px 10px;
            font-size: 13px;
            min-height: 22px;
            color: #1C1917;
            selection-background-color: #1C1917;
            selection-color: #FFFFFF;
        }

        QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus,
        QDateTimeEdit:focus, QComboBox:focus {
            border: 1px solid #1C1917;
            background: #FFFFFF;
        }

        QLineEdit:disabled {
            background: #F5F4F1;
            color: #A8A29E;
        }

        QComboBox::drop-down {
            border: none;
            width: 22px;
            background: transparent;
        }

        QComboBox::down-arrow {
            width: 10px;
            height: 10px;
        }

        QComboBox QAbstractItemView {
            background: #FFFFFF;
            border: 1px solid #D6D3CB;
            padding: 4px;
            selection-background-color: #F5F4F1;
            selection-color: #1C1917;
            outline: none;
        }

        QComboBox QAbstractItemView::item {
            padding: 6px 12px;
            min-height: 24px;
            border: none;
            border-radius: 4px;
        }

        QComboBox QAbstractItemView::item:selected {
            background: #F5F4F1;
        }

        QTableWidget {
            background: #FFFFFF;
            alternate-background-color: #FBFAF8;
            border: 1px solid #E7E5E0;
            border-radius: 8px;
            gridline-color: #F5F4F1;
            selection-background-color: #FDEBEC;
            selection-color: #9F2F2D;
            outline: none;
        }

        QTableWidget::item {
            padding: 8px 10px;
            border: none;
        }

        QTableWidget::item:hover {
            background: #F5F4F1;
        }

        QTableWidget::item:selected {
            background: #FDEBEC;
            color: #9F2F2D;
        }

        QHeaderView::section {
            background: #FAFAF9;
            color: #57534E;
            border: none;
            border-bottom: 1px solid #E7E5E0;
            border-right: 1px solid #F5F4F1;
            padding: 10px 12px;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        QHeaderView::section:hover {
            background: #F5F4F1;
            color: #1C1917;
        }

        /* ===== 列表 ===== */
        QListWidget {
            background: #FFFFFF;
            border: 1px solid #E7E5E0;
            border-radius: 6px;
            padding: 4px;
            outline: none;
        }

        QListWidget::item {
            padding: 8px 12px;
            border-radius: 4px;
            border-bottom: 1px solid #F5F4F1;
        }

        QListWidget::item:selected {
            background: #FDEBEC;
            color: #9F2F2D;
        }

        /* ===== 标签 ===== */
        QLabel[cssClass="title"] {
            font-size: 22px;
            font-weight: 700;
            color: #1C1917;
            letter-spacing: -0.5px;
        }

        QLabel[cssClass="subtitle"] {
            font-size: 14px;
            font-weight: 600;
            color: #1C1917;
        }

        QLabel[cssClass="sectionLabel"] {
            font-size: 15px;
            font-weight: 600;
            color: #1C1917;
            padding-top: 4px;
        }

        QLabel[cssClass="stats"] {
            font-size: 13px;
            font-weight: 500;
            color: #1C1917;
            background: #FFFFFF;
            border: 1px solid #E7E5E0;
            border-radius: 8px;
            padding: 12px 16px;
        }

        QLabel[cssClass="muted"] {
            color: #A8A29E;
            font-size: 12px;
        }

        QLabel[cssClass="placeholder"] {
            color: #A8A29E;
            font-size: 15px;
            font-weight: 500;
        }

        QLabel[cssClass="badge"] {
            font-size: 11px;
            font-weight: 600;
            color: #1C1917;
            background: #F5F4F1;
            border: 1px solid #E7E5E0;
            border-radius: 10px;
            padding: 2px 10px;
        }

        QLabel[cssClass="badgeSuccess"] {
            font-size: 11px;
            font-weight: 600;
            color: #15803D;
            background: #EDF3EC;
            border: 1px solid #D1E7D0;
            border-radius: 10px;
            padding: 2px 10px;
        }

        QLabel[cssClass="badgeWarning"] {
            font-size: 11px;
            font-weight: 600;
            color: #B45309;
            background: #FBF3DB;
            border: 1px solid #FDE68A;
            border-radius: 10px;
            padding: 2px 10px;
        }

        QLabel[cssClass="badgeDanger"] {
            font-size: 11px;
            font-weight: 600;
            color: #9F2F2D;
            background: #FDEBEC;
            border: 1px solid #FECACA;
            border-radius: 10px;
            padding: 2px 10px;
        }

        QLabel[cssClass="badgeInfo"] {
            font-size: 11px;
            font-weight: 600;
            color: #1F6C9F;
            background: #E1F3FE;
            border: 1px solid #BAE6FD;
            border-radius: 10px;
            padding: 2px 10px;
        }

        /* ===== 工具提示 ===== */
        QToolTip {
            background: #1C1917;
            color: #FFFFFF;
            border: none;
            border-radius: 4px;
            padding: 6px 10px;
            font-size: 12px;
        }

        /* ===== 滚动条 ===== */
        QScrollBar:vertical {
            background: transparent;
            width: 12px;
            border: none;
            margin: 4px;
        }

        QScrollBar::handle:vertical {
            background: #D6D3CB;
            border-radius: 4px;
            min-height: 36px;
        }

        QScrollBar::handle:vertical:hover {
            background: #A8A29E;
        }

        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
            height: 0;
        }

        QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {
            background: transparent;
        }

        QScrollBar:horizontal {
            background: transparent;
            height: 12px;
            border: none;
            margin: 4px;
        }

        QScrollBar::handle:horizontal {
            background: #D6D3CB;
            border-radius: 4px;
            min-width: 36px;
        }

        QScrollBar::handle:horizontal:hover {
            background: #A8A29E;
        }

        QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {
            width: 0;
        }

        QScrollBar::add-page:horizontal, QScrollBar::sub-page:horizontal {
            background: transparent;
        }

        /* ===== 复选框 ===== */
        QCheckBox {
            spacing: 8px;
            color: #1C1917;
        }

        QCheckBox::indicator {
            width: 16px;
            height: 16px;
            border-radius: 4px;
            border: 1px solid #D6D3CB;
            background: #FFFFFF;
        }

        QCheckBox::indicator:checked {
            background: #1C1917;
            border: 1px solid #1C1917;
        }

        /* ===== 滑块 ===== */
        QSlider::groove:horizontal {
            height: 4px;
            background: #E7E5E0;
            border-radius: 2px;
        }

        QSlider::handle:horizontal {
            background: #1C1917;
            width: 14px;
            height: 14px;
            margin: -5px 0;
            border-radius: 7px;
        }

        QSlider::handle:horizontal:hover {
            background: #3F3F46;
        }

        QSlider::sub-page:horizontal {
            background: #1C1917;
            border-radius: 2px;
        }

        /* ===== 菜单栏 ===== */
        QMenuBar {
            background: #FAFAF9;
            color: #1C1917;
            border-bottom: 1px solid #E7E5E0;
            padding: 4px;
        }

        QMenuBar::item {
            background: transparent;
            padding: 6px 14px;
            border-radius: 4px;
        }

        QMenuBar::item:selected {
            background: #F5F4F1;
        }

        QMenu {
            background: #FFFFFF;
            border: 1px solid #E7E5E0;
            border-radius: 6px;
            padding: 4px;
        }

        QMenu::item {
            padding: 8px 24px 8px 14px;
            border-radius: 4px;
        }

        QMenu::item:selected {
            background: #F5F4F1;
        }

        /* ===== 状态栏 ===== */
        QStatusBar {
            background: #FAFAF9;
            color: #57534E;
            border-top: 1px solid #E7E5E0;
            font-size: 12px;
        }

        QStatusBar::item {
            border: none;
        }

        /* ===== 工具栏 ===== */
        QToolBar {
            background: #FFFFFF;
            border-bottom: 1px solid #E7E5E0;
            border: none;
            padding: 6px 12px;
            spacing: 4px;
        }

        QToolBar::separator {
            background: #E7E5E0;
            width: 1px;
            margin: 6px 8px;
        }

        /* ===== 对话框 ===== */
        QDialog {
            background: #FAFAF9;
        }

        /* ===== 进度条 ===== */
        QProgressBar {
            background: #E7E5E0;
            border: none;
            border-radius: 4px;
            height: 6px;
            text-align: center;
            font-size: 11px;
        }

        QProgressBar::chunk {
            background: #1C1917;
            border-radius: 4px;
        }
    )");
    css.replace(QStringLiteral("%1"), fontFamily);
    return css;
}

} // namespace Theme