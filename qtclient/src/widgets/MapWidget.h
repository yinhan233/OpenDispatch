#pragma once

#include <QWidget>
#include <QList>
#include <QHash>
#include <QPixmap>
#include <QPointF>
#include <QDateTime>
#include <QTimer>
#include "../Models.h"

struct AnimLocation {
    qulonglong id = 0;
    QString name;
    double lng = 0.0;
    double lat = 0.0;
};

struct AnimOrder {
    qulonglong id = 0;
    qulonglong pickupLocId = 0;
    qulonglong deliveryLocId = 0;
    int serviceTime = 0;
};

struct AnimVehicle {
    qulonglong id = 0;
    QString toolType;
    QString status = "IDLE";
    qulonglong curLocId = 0;
    double speed = 30.0;
};

struct AnimRouteLeg {
    qulonglong orderId = 0;
    QDateTime plannedStart;
    QDateTime plannedEnd;
    int idleBefore = 0;
};

struct AnimVehicleRoute {
    qulonglong vehicleId = 0;
    QList<AnimRouteLeg> route;
};

// A waypoint in a vehicle's animated path: position + time when vehicle arrives
struct AnimWaypoint {
    qulonglong locId = 0;
    QPointF pos;          // lng/lat
    QDateTime arriveTime; // when vehicle is here
    QDateTime leaveTime;  // when vehicle departs (after service/idle)
    bool isPickup = false;
    bool isDelivery = false;
    qulonglong orderId = 0;
};

class MapWidget : public QWidget {
    Q_OBJECT
public:
    explicit MapWidget(QWidget *parent = nullptr);

    void setData(const QList<AnimLocation> &locs,
                 const QList<AnimOrder> &orders,
                 const QList<AnimVehicle> &vehicles,
                 const QList<AnimVehicleRoute> &routes);

    void clear();

public slots:
    void play();
    void pause();
    void reset();
    void setSpeed(double factor); // 1.0, 2.0, 5.0, 10.0

signals:
    void timeChanged(const QDateTime &t);
    void progressChanged(int percent);

protected:
    void paintEvent(QPaintEvent *event) override;
    QSize minimumSizeHint() const override;
    QSize sizeHint() const override;

private slots:
    void onTick();

private:
    QList<AnimLocation> m_locations;
    QHash<qulonglong, AnimLocation> m_locById;
    QList<AnimOrder> m_orders;
    QHash<qulonglong, AnimOrder> m_orderById;
    QList<AnimVehicle> m_vehicles;
    QHash<qulonglong, AnimVehicle> m_vehicleById;
    QList<AnimVehicleRoute> m_routes;

    // Built from routes + orders + vehicle current loc
    QList<QList<AnimWaypoint>> m_paths; // per vehicle index aligned with m_activeVehicles
    QList<qulonglong> m_activeVehicleIds;

    QHash<QString, QPixmap> m_iconCache;
    QPixmap m_defaultIcon;

    // Time playback
    QTimer *m_timer;
    QDateTime m_simStart;
    QDateTime m_simEnd;
    QDateTime m_simNow;
    double m_speedFactor = 5.0;
    bool m_playing = false;
    static const int TickMs = 50;
    static const double SimSecPerRealSec; // base: 1 real sec = N sim secs

    // Map bounds
    double m_minLng = 0, m_maxLng = 0, m_minLat = 0, m_maxLat = 0;
    bool m_boundsValid = false;

    void loadIcons();
    QPixmap iconForType(const QString &toolType) const;
    void computeBounds();
    void buildPaths();
    QPointF lngLatToPixel(const QPointF &lngLat) const;
    QPointF lngLatToPixel(double lng, double lat) const;
    int leftMargin() const;
    int topMargin() const;
    int rightMargin() const;
    int bottomMargin() const;

    // For a given vehicle path and sim time, get interpolated pixel position
    bool vehiclePositionAt(const QList<AnimWaypoint> &path, const QDateTime &t, QPointF &outPixel) const;
};
