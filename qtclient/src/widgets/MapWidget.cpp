#include "MapWidget.h"
#include <QPainter>
#include <QPainterPath>
#include <QPaintEvent>
#include <QDir>
#include <cmath>
#include "../Theme.h"

const double MapWidget::SimSecPerRealSec = 120.0;

MapWidget::MapWidget(QWidget *parent) : QWidget(parent) {
    setMinimumSize(600, 400);
    setAutoFillBackground(true);
    setBackgroundRole(QPalette::Base);

    m_timer = new QTimer(this);
    m_timer->setInterval(TickMs);
    connect(m_timer, &QTimer::timeout, this, &MapWidget::onTick);

    loadIcons();
}

void MapWidget::loadIcons() {
    static const QStringList iconNames = {
        "电瓶车", "三轮车", "微型面包车", "厢式货车",
        "吉普车", "平板卡车", "冷藏车", "大型重卡"
    };
    for (const auto &name : iconNames) {
        QString path = QString(":/icons/icon/%1.png").arg(name);
        QPixmap pix(path);
        if (!pix.isNull()) {
            m_iconCache[name] = pix.scaled(36, 36, Qt::KeepAspectRatio, Qt::SmoothTransformation);
        }
    }
    if (m_iconCache.isEmpty()) {
        m_defaultIcon = QPixmap(36, 36);
        m_defaultIcon.fill(Qt::gray);
    } else {
        m_defaultIcon = m_iconCache.constBegin().value();
    }
}

QPixmap MapWidget::iconForType(const QString &toolType) const {
    auto it = m_iconCache.constFind(toolType);
    if (it != m_iconCache.end()) return it.value();
    return m_defaultIcon;
}

void MapWidget::clear() {
    m_locations.clear();
    m_locById.clear();
    m_orders.clear();
    m_orderById.clear();
    m_vehicles.clear();
    m_vehicleById.clear();
    m_routes.clear();
    m_paths.clear();
    m_activeVehicleIds.clear();
    m_boundsValid = false;
    m_playing = false;
    m_timer->stop();
    update();
}

void MapWidget::setData(const QList<AnimLocation> &locs,
                         const QList<AnimOrder> &orders,
                         const QList<AnimVehicle> &vehicles,
                         const QList<AnimVehicleRoute> &routes) {
    m_locations = locs;
    m_orders = orders;
    m_vehicles = vehicles;
    m_routes = routes;

    m_locById.clear();
    for (const auto &l : locs) m_locById.insert(l.id, l);
    m_orderById.clear();
    for (const auto &o : orders) m_orderById.insert(o.id, o);
    m_vehicleById.clear();
    for (const auto &v : vehicles) m_vehicleById.insert(v.id, v);

    computeBounds();
    buildPaths();

    if (!m_paths.isEmpty()) {
        m_simStart = QDateTime();
        m_simEnd = QDateTime();
        for (const auto &path : m_paths) {
            for (const auto &wp : path) {
                if (!m_simStart.isValid() || wp.arriveTime < m_simStart) m_simStart = wp.arriveTime;
                if (!m_simEnd.isValid() || wp.leaveTime > m_simEnd) m_simEnd = wp.leaveTime;
            }
        }
        if (m_simStart.isValid() && m_simEnd.isValid()) {
            qint64 range = m_simStart.secsTo(m_simEnd);
            if (range <= 0) range = 3600;
            m_simStart = m_simStart.addSecs(-range / 20);
            m_simEnd = m_simEnd.addSecs(range / 20);
        }
        m_simNow = m_simStart;
    } else {
        m_simStart = QDateTime::currentDateTime();
        m_simEnd = m_simStart.addSecs(3600);
        m_simNow = m_simStart;
    }

    update();
}

void MapWidget::computeBounds() {
    m_boundsValid = false;
    if (m_locations.isEmpty()) return;
    m_minLng = m_maxLng = m_locations[0].lng;
    m_minLat = m_maxLat = m_locations[0].lat;
    for (const auto &l : m_locations) {
        if (l.lng < m_minLng) m_minLng = l.lng;
        if (l.lng > m_maxLng) m_maxLng = l.lng;
        if (l.lat < m_minLat) m_minLat = l.lat;
        if (l.lat > m_maxLat) m_maxLat = l.lat;
    }
    double padLng = (m_maxLng - m_minLng) * 0.15 + 0.01;
    double padLat = (m_maxLat - m_minLat) * 0.15 + 0.01;
    m_minLng -= padLng; m_maxLng += padLng;
    m_minLat -= padLat; m_maxLat += padLat;
    m_boundsValid = true;
}

void MapWidget::buildPaths() {
    m_paths.clear();
    m_activeVehicleIds.clear();

    for (const auto &vr : m_routes) {
        if (vr.route.isEmpty()) continue;
        auto vIt = m_vehicleById.constFind(vr.vehicleId);
        if (vIt == m_vehicleById.end()) continue;
        const auto &veh = vIt.value();

        QList<AnimWaypoint> path;
        AnimWaypoint wp;

        wp.locId = veh.curLocId;
        auto locIt = m_locById.constFind(veh.curLocId);
        if (locIt != m_locById.end()) {
            wp.pos = QPointF(locIt.value().lng, locIt.value().lat);
        }
        // first leg's plannedStart is when vehicle departs
        wp.arriveTime = vr.route.first().plannedStart.addSecs(-vr.route.first().idleBefore);
        wp.leaveTime = vr.route.first().plannedStart;
        path.append(wp);

        // For each leg
        for (const auto &leg : vr.route) {
            auto oIt = m_orderById.constFind(leg.orderId);
            if (oIt == m_orderById.end()) continue;
            const auto &ord = oIt.value();

            // Pickup waypoint
            auto pLoc = m_locById.constFind(ord.pickupLocId);
            if (pLoc != m_locById.end()) {
                AnimWaypoint pwp;
                pwp.locId = ord.pickupLocId;
                pwp.pos = QPointF(pLoc.value().lng, pLoc.value().lat);
                pwp.arriveTime = leg.plannedStart;
                pwp.leaveTime = leg.plannedStart.addSecs(ord.serviceTime);
                pwp.isPickup = true;
                pwp.orderId = leg.orderId;
                path.append(pwp);
            }

            // Delivery waypoint
            auto dLoc = m_locById.constFind(ord.deliveryLocId);
            if (dLoc != m_locById.end()) {
                AnimWaypoint dwp;
                dwp.locId = ord.deliveryLocId;
                dwp.pos = QPointF(dLoc.value().lng, dLoc.value().lat);
                dwp.arriveTime = leg.plannedEnd;
                dwp.leaveTime = leg.plannedEnd;
                dwp.isDelivery = true;
                dwp.orderId = leg.orderId;
                path.append(dwp);
            }
        }

        m_paths.append(path);
        m_activeVehicleIds.append(vr.vehicleId);
    }
}

int MapWidget::leftMargin() const { return 60; }
int MapWidget::topMargin() const { return 16; }
int MapWidget::rightMargin() const { return 30; }
int MapWidget::bottomMargin() const { return 50; }

QPointF MapWidget::lngLatToPixel(double lng, double lat) const {
    return lngLatToPixel(QPointF(lng, lat));
}

QPointF MapWidget::lngLatToPixel(const QPointF &lngLat) const {
    int w = width() - leftMargin() - rightMargin();
    int h = height() - topMargin() - bottomMargin();
    double rangeLng = m_maxLng - m_minLng;
    double rangeLat = m_maxLat - m_minLat;
    if (rangeLng <= 0) rangeLng = 1.0;
    if (rangeLat <= 0) rangeLat = 1.0;
    double nx = (lngLat.x() - m_minLng) / rangeLng;
    double ny = (m_maxLat - lngLat.y()) / rangeLat; // invert Y (lat)
    double px = leftMargin() + nx * w;
    double py = topMargin() + ny * h;
    return QPointF(px, py);
}

bool MapWidget::vehiclePositionAt(const QList<AnimWaypoint> &path, const QDateTime &t, QPointF &outPixel) const {
    if (path.isEmpty()) return false;
    if (t < path.first().arriveTime) {
        outPixel = lngLatToPixel(path.first().pos);
        return true;
    }
    for (int i = 0; i < path.size() - 1; ++i) {
        const auto &a = path[i];
        const auto &b = path[i + 1];
        if (t >= a.leaveTime && t <= b.arriveTime) {
            qint64 travel = a.leaveTime.secsTo(b.arriveTime);
            qint64 elapsed = a.leaveTime.secsTo(t);
            double frac = (travel > 0) ? static_cast<double>(elapsed) / travel : 0.0;
            frac = std::clamp(frac, 0.0, 1.0);
            QPointF p = a.pos + (b.pos - a.pos) * frac;
            outPixel = lngLatToPixel(p);
            return true;
        }
        if (t >= a.arriveTime && t <= a.leaveTime) {
            outPixel = lngLatToPixel(a.pos);
            return true;
        }
    }
    outPixel = lngLatToPixel(path.last().pos);
    return true;
}

void MapWidget::play() {
    if (m_simNow >= m_simEnd) m_simNow = m_simStart;
    m_playing = true;
    m_timer->start(TickMs);
    update();
}

void MapWidget::pause() {
    m_playing = false;
    m_timer->stop();
    update();
}

void MapWidget::reset() {
    m_playing = false;
    m_timer->stop();
    m_simNow = m_simStart;
    emit timeChanged(m_simNow);
    emit progressChanged(0);
    update();
}

void MapWidget::setSpeed(double factor) {
    m_speedFactor = factor;
}

void MapWidget::onTick() {
    if (!m_playing) return;
    double advanceSec = SimSecPerRealSec * m_speedFactor * (TickMs / 1000.0);
    m_simNow = m_simNow.addMSecs(static_cast<qint64>(advanceSec * 1000));
    if (m_simNow >= m_simEnd) {
        m_simNow = m_simEnd;
        m_playing = false;
        m_timer->stop();
    }
    emit timeChanged(m_simNow);
    if (m_simStart.secsTo(m_simEnd) > 0) {
        int pct = static_cast<int>(m_simStart.secsTo(m_simNow) * 100.0 / m_simStart.secsTo(m_simEnd));
        emit progressChanged(std::clamp(pct, 0, 100));
    }
    update();
}

QSize MapWidget::minimumSizeHint() const { return QSize(600, 400); }
QSize MapWidget::sizeHint() const { return QSize(900, 600); }
//地图背景相关
void MapWidget::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    p.fillRect(rect(), QColor("#FAFAF9"));
    QRect mapArea(leftMargin(), topMargin(),
                  width() - leftMargin() - rightMargin(),
                  height() - topMargin() - bottomMargin());
    p.fillRect(mapArea, QColor("#FFFFFF"));
    p.setRenderHint(QPainter::Antialiasing, true);
    p.setPen(QPen(QColor("#E7E5E0"), 1));
    p.setBrush(Qt::NoBrush);
    p.drawRoundedRect(mapArea, 8, 8);

    if (!m_boundsValid || m_locations.isEmpty()) {
        p.setPen(Theme::Colors::TextMuted());
        QFont ph = font();
        ph.setPointSize(13);
        p.setFont(ph);
        p.drawText(rect(), Qt::AlignCenter, "无地图数据。请先生成调度方案。");
        return;
    }

    // 经纬度网格线 + 坐标标签
    auto niceStep = [](double range) -> double {
        if (range <= 0) return 1.0;
        double raw = range / 6.0;
        double mag = std::pow(10.0, std::floor(std::log10(raw)));
        double norm = raw / mag;
        double step;
        if (norm < 1.5) step = 1.0;
        else if (norm < 3.0) step = 2.0;
        else if (norm < 7.0) step = 5.0;
        else step = 10.0;
        return step * mag;
    };
    auto decimalsFor = [](double step) -> int {
        if (step >= 1.0) return 0;
        if (step >= 0.1) return 1;
        if (step >= 0.01) return 2;
        return 3;
    };
    double stepLng = niceStep(m_maxLng - m_minLng);
    double stepLat = niceStep(m_maxLat - m_minLat);
    int decLng = decimalsFor(stepLng);
    int decLat = decimalsFor(stepLat);

    QFont gridFont = font();
    gridFont.setPointSize(7);
    p.setFont(gridFont);

    p.setClipRect(mapArea);

    // 经度竖线 + 底部标签
    for (double lng = std::ceil(m_minLng / stepLng) * stepLng;
         lng <= m_maxLng + 1e-9; lng += stepLng) {
        QPointF top = lngLatToPixel(lng, m_maxLat);
        QPointF bot = lngLatToPixel(lng, m_minLat);
        p.setPen(QPen(QColor("#E7E5E0"), 1, Qt::DotLine));
        p.drawLine(top, bot);
        p.setPen(Theme::Colors::TextMuted());
        QString lbl = QString::number(lng, 'f', decLng) + "°E";
        p.drawText(QRect(static_cast<int>(top.x()) - 30, mapArea.bottom() + 6, 60, 14),
                   Qt::AlignCenter, lbl);
    }

    // 纬度横线 + 左侧标签
    for (double lat = std::ceil(m_minLat / stepLat) * stepLat;
         lat <= m_maxLat + 1e-9; lat += stepLat) {
        QPointF left = lngLatToPixel(m_minLng, lat);
        QPointF right = lngLatToPixel(m_maxLng, lat);
        p.setPen(QPen(QColor("#E7E5E0"), 1, Qt::DotLine));
        p.drawLine(left, right);
        p.setPen(Theme::Colors::TextMuted());
        QString lbl = QString::number(lat, 'f', decLat) + "°N";
        p.drawText(QRect(0, static_cast<int>(left.y()) - 7, leftMargin() - 8, 14),
                   Qt::AlignRight | Qt::AlignVCenter, lbl);
    }

    p.setClipping(false);

    // 画路径，虚线
    for (int vi = 0; vi < m_paths.size(); ++vi) {
        const auto &path = m_paths[vi];
        if (path.size() < 2) continue;
        QColor traceColor = Theme::Colors::orderColor(static_cast<int>(m_activeVehicleIds[vi]));
        traceColor.setAlpha(60);
        p.setPen(QPen(traceColor, 2, Qt::DashLine));
        for (int i = 0; i < path.size() - 1; ++i) {
            QPointF a = lngLatToPixel(path[i].pos);
            QPointF b = lngLatToPixel(path[i + 1].pos);
            p.drawLine(a, b);
        }
    }

    // 画路径
    for (int vi = 0; vi < m_paths.size(); ++vi) {
        const auto &path = m_paths[vi];
        if (path.size() < 2) continue;
        QColor solidColor = Theme::Colors::orderColor(static_cast<int>(m_activeVehicleIds[vi]));
        p.setPen(QPen(solidColor, 3));
        for (int i = 0; i < path.size() - 1; ++i) {
            const auto &a = path[i];
            const auto &b = path[i + 1];
            if (m_simNow < a.leaveTime) break;
            QPointF pa = lngLatToPixel(a.pos);
            QPointF pb;
            if (m_simNow >= b.arriveTime) {
                pb = lngLatToPixel(b.pos);
                p.drawLine(pa, pb);
            } else {
                qint64 travel = a.leaveTime.secsTo(b.arriveTime);
                qint64 elapsed = a.leaveTime.secsTo(m_simNow);
                double frac = (travel > 0) ? static_cast<double>(elapsed) / travel : 0.0;
                frac = std::clamp(frac, 0.0, 1.0);
                QPointF cur = a.pos + (b.pos - a.pos) * frac;
                pb = lngLatToPixel(cur);
                p.drawLine(pa, pb);
                break;
            }
        }
    }

    //画地标
    for (const auto &loc : m_locations) {
        QPointF pt = lngLatToPixel(loc.lng, loc.lat);
        p.setPen(QPen(QColor("#1C1917"), 1));
        p.setBrush(QColor("#FFFFFF"));
        p.drawEllipse(pt, 7, 7);
        p.setBrush(QColor("#1C1917"));
        p.drawEllipse(pt, 4, 4);

        //标签
        p.setPen(Theme::Colors::TextPrimary());
        QFont lblFont = font();
        lblFont.setPointSize(8);
        lblFont.setBold(true);
        p.setFont(lblFont);
        QString name = loc.name;
        if (name.length() > 14) name = name.left(12) + "...";
        QRect lblRect(static_cast<int>(pt.x()) + 10, static_cast<int>(pt.y()) - 18, 160, 16);
        p.drawText(lblRect, Qt::AlignLeft | Qt::AlignVCenter, name);
    }

    //车辆渲染
    for (int vi = 0; vi < m_paths.size(); ++vi) {
        const auto &path = m_paths[vi];
        if (path.isEmpty()) continue;
        QPointF vpos;
        if (!vehiclePositionAt(path, m_simNow, vpos)) continue;

        qulonglong vid = m_activeVehicleIds[vi];
        auto vIt = m_vehicleById.constFind(vid);
        QString toolType = (vIt != m_vehicleById.end()) ? vIt.value().toolType : "";
        QPixmap icon = iconForType(toolType);

        //车辆位置
        int iconSz = 36;
        QRect iconRect(static_cast<int>(vpos.x()) - iconSz / 2,
                       static_cast<int>(vpos.y()) - iconSz / 2,
                       iconSz, iconSz);
        p.drawPixmap(iconRect, icon);

        //ID渲染
        QString badge = QString("车%1").arg(vid);
        QFont badgeFont = font();
        badgeFont.setPointSize(7);
        badgeFont.setBold(true);
        p.setFont(badgeFont);
        QRectF badgeRect(vpos.x() + 12, vpos.y() - 22, 50, 14);
        p.setPen(Qt::NoPen);
        p.setBrush(Theme::Colors::orderColor(static_cast<int>(vid)));
        p.drawRoundedRect(badgeRect, 3, 3);
        p.setPen(Qt::white);
        p.drawText(badgeRect, Qt::AlignCenter, badge);
    }

    //底部
    p.setPen(Theme::Colors::TextMuted());
    QFont legFont = font();
    legFont.setPointSize(8);
    p.setFont(legFont);
    int legY = height() - bottomMargin() + 28;
    p.drawText(QRect(leftMargin(), legY, 240, 16), Qt::AlignLeft,
               QString("%1x · %2").arg(m_speedFactor, 0, 'f', 1).arg(m_playing ? "▶ 播放中" : "⏸ 已暂停"));
}
