#pragma once

#include <QWidget>
#include <QList>
#include "Models.h"

class GanttChart : public QWidget {
    Q_OBJECT
public:
    explicit GanttChart(QWidget *parent = nullptr);

    void setSchedule(const ScheduleResult &result);
    void clear();

    QSize minimumSizeHint() const override;
    QSize sizeHint() const override;

protected:
    void paintEvent(QPaintEvent *event) override;

private:
    ScheduleResult m_schedule;
    QDateTime m_startTime;
    QDateTime m_endTime;

    void computeTimeRange();
    int timeToX(const QDateTime &t) const;
    int leftMargin() const;
    int rowHeight() const;
    int topMargin() const;
};
