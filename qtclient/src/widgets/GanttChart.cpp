#include "GanttChart.h"
#include <QPainter>
#include <QPainterPath>
#include <cmath>
#include "../Theme.h"

GanttChart::GanttChart(QWidget *parent) : QWidget(parent) {
    setMinimumSize(600, 300);
    setAutoFillBackground(false);
    setAttribute(Qt::WA_OpaquePaintEvent, false);
}

void GanttChart::setSchedule(const ScheduleResult &result) {
    m_schedule = result;
    computeTimeRange();
    update();
}

void GanttChart::clear() {
    m_schedule = ScheduleResult();
    update();
}

void GanttChart::computeTimeRange() {
    m_startTime = QDateTime();
    m_endTime = QDateTime();
    for (const auto &vr : m_schedule.vehicles) {
        for (const auto &leg : vr.route) {
            if (!m_startTime.isValid() || leg.plannedStart < m_startTime) m_startTime = leg.plannedStart;
            if (!m_endTime.isValid() || leg.plannedEnd > m_endTime) m_endTime = leg.plannedEnd;
        }
    }
    if (!m_startTime.isValid()) {
        m_startTime = QDateTime::currentDateTime();
        m_endTime = m_startTime.addSecs(3600);
    }
    qint64 range = m_startTime.secsTo(m_endTime);
    if (range <= 0) range = 3600;
    m_startTime = m_startTime.addSecs(-range / 20);
    m_endTime = m_endTime.addSecs(range / 20);
}

int GanttChart::leftMargin() const { return 90; }
int GanttChart::topMargin() const { return 56; }
int GanttChart::rowHeight() const { return 40; }

int GanttChart::timeToX(const QDateTime &t) const {
    qint64 total = m_startTime.secsTo(m_endTime);
    if (total <= 0) return leftMargin();
    qint64 elapsed = m_startTime.secsTo(t);
    int w = width() - leftMargin() - 20;
    return leftMargin() + static_cast<int>(elapsed * 1.0 / total * w);
}

QSize GanttChart::minimumSizeHint() const {
    int rows = qMax(1, m_schedule.vehicles.size());
    int h = topMargin() + rows * rowHeight() + 40;
    return QSize(600, h);
}
QSize GanttChart::sizeHint() const {
    int rows = qMax(1, m_schedule.vehicles.size());
    int h = topMargin() + rows * rowHeight() + 40;
    return QSize(800, qMax(400, h));
}

void GanttChart::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    p.fillRect(rect(), Theme::Colors::CardBg());

    if (m_schedule.vehicles.isEmpty()) {
        p.setPen(Theme::Colors::TextMuted());
        QFont phFont = font();
        phFont.setPointSize(13);
        p.setFont(phFont);
        p.drawText(rect(), Qt::AlignCenter,
                   "尚无调度数据。请点击「执行调度」按钮生成。");
        return;
    }

    static const QColor colors[] = {
        QColor("#1C1917"), QColor("#9F2F2D"), QColor("#15803D"),
        QColor("#B45309"), QColor("#1F6C9F"), QColor("#7C3AED"),
        QColor("#0E7490"), QColor("#BE185D"),
    };
    const int nColors = sizeof(colors) / sizeof(colors[0]);

    // 标题
    p.setPen(Theme::Colors::TextPrimary());
    QFont titleFont = font();
    titleFont.setBold(true);
    titleFont.setPointSize(11);
    p.setFont(titleFont);
    p.drawText(QRect(0, 6, width(), 30), Qt::AlignCenter,
               QString("调度甘特图 — 总闲置: %1 秒   收益: %2 元   %3")
                   .arg(m_schedule.stats.totalIdle)
                   .arg(m_schedule.stats.totalRevenue, 0, 'f', 1)
                   .arg(m_schedule.stats.feasible ? "(可行)" : "(不可行)"));

    // 时间轴
    p.setPen(Theme::Colors::TextMuted());
    QFont smallFont = font();
    smallFont.setPointSize(8);
    p.setFont(smallFont);
    int axisY = topMargin() - 8;
    p.drawLine(leftMargin(), axisY, width() - 20, axisY);
    qint64 totalSecs = m_startTime.secsTo(m_endTime);
    int tickCount = 6;
    for (int i = 0; i <= tickCount; ++i) {
        QDateTime t = m_startTime.addSecs(totalSecs * i / tickCount);
        int x = timeToX(t);
        p.drawLine(x, axisY, x, axisY + 4);
        p.drawText(QRect(x - 40, axisY - 16, 80, 14), Qt::AlignCenter,
                   t.toString("hh:mm"));
    }

    // 行
    int y = topMargin();
    int rowIdx = 0;
    for (const auto &vr : m_schedule.vehicles) {
        if (rowIdx % 2 == 0) {
            p.fillRect(QRect(leftMargin(), y, width() - leftMargin() - 20, rowHeight()),
                       Theme::Colors::RowStripe());
        }

        p.setPen(Theme::Colors::TextPrimary());
        p.setFont(font());
        p.drawText(QRect(5, y, leftMargin() - 10, rowHeight()), Qt::AlignVCenter | Qt::AlignRight,
                   QString("车辆 %1").arg(vr.vehicleId));

        p.setPen(QPen(Theme::Colors::Border(), 1, Qt::DashLine));
        p.drawLine(leftMargin(), y + rowHeight(), width() - 20, y + rowHeight());

        for (const auto &leg : vr.route) {
            int x1 = timeToX(leg.plannedStart);
            int x2 = timeToX(leg.plannedEnd);
            if (x2 <= x1) x2 = x1 + 2;

            QColor color = colors[leg.orderId % nColors];
            QRect bar(x1, y + 8, x2 - x1, rowHeight() - 16);

            QPainterPath barPath;
            barPath.addRoundedRect(bar, 6, 6);

            p.setPen(QPen(color.darker(130), 1));
            p.setBrush(color);
            p.drawPath(barPath);

            p.setPen(Qt::white);
            QFont barFont = font();
            barFont.setPointSize(8);
            barFont.setBold(true);
            p.setFont(barFont);
            QString label = QString("#%1").arg(leg.orderId);
            if (bar.width() > 30) {
                p.drawText(bar, Qt::AlignCenter, label);
            }

            if (leg.idleBefore > 0 && leg.seq > 1) {
                p.setPen(Theme::Colors::IdleAnnot());
                p.setFont(smallFont);
                QString idleStr = QString("闲置 %1 秒").arg(leg.idleBefore);
                p.drawText(QRect(x1 - 70, y - 2, 70, 12), Qt::AlignRight, idleStr);
            }
        }

        y += rowHeight();
        rowIdx++;
    }

    if (!m_schedule.unassigned.isEmpty()) {
        y += 8;
        p.setPen(Theme::Colors::Unassigned());
        QFont warnFont = font();
        warnFont.setBold(true);
        p.setFont(warnFont);
        QStringList ids;
        for (qulonglong id : m_schedule.unassigned) ids << QString::number(id);
        p.drawText(QRect(5, y, width() - 10, 20), Qt::AlignLeft,
                   QString("未分配订单 (%1): %2")
                       .arg(m_schedule.unassigned.size())
                       .arg(ids.join(", ")));
    }
}
