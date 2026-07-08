#pragma once

#include <QString>
#include <QDateTime>
#include <QList>
#include <QVariantMap>
#include <QJsonObject>
#include <QJsonArray>
#include <QJsonDocument>

// ---------- Location ----------
struct LocationData {
    qulonglong id = 0;
    QString name;
    double lng = 0.0;
    double lat = 0.0;

    static LocationData fromJson(const QJsonObject &o) {
        LocationData d;
        d.id = o["id"].toVariant().toULongLong();
        d.name = o["name"].toString();
        d.lng = o["lng"].toDouble();
        d.lat = o["lat"].toDouble();
        return d;
    }
    QJsonObject toJson() const {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(id));
        o["name"] = name;
        o["lng"] = lng;
        o["lat"] = lat;
        return o;
    }
};

// ---------- Order ----------
struct OrderData {
    qulonglong id = 0;
    qulonglong taskId = 0;
    qulonglong pickupLocId = 0;
    qulonglong deliveryLocId = 0;
    QDateTime timeStart;
    QDateTime timeEnd;
    double revenue = 0.0;
    double weight = 0.0;
    double volume = 0.0;
    int serviceTime = 0;
    QString status = "UNASSIGNED";
    QDateTime createdAt;

    static OrderData fromJson(const QJsonObject &o) {
        OrderData d;
        d.id = o["id"].toVariant().toULongLong();
        d.taskId = o["taskId"].toVariant().toULongLong();
        d.pickupLocId = o["pickupLocId"].toVariant().toULongLong();
        d.deliveryLocId = o["deliveryLocId"].toVariant().toULongLong();
        d.timeStart = QDateTime::fromString(o["timeStart"].toString(), Qt::ISODate);
        d.timeEnd = QDateTime::fromString(o["timeEnd"].toString(), Qt::ISODate);
        d.revenue = o["revenue"].toDouble();
        d.weight = o["weight"].toDouble();
        d.volume = o["volume"].toDouble();
        d.serviceTime = o["serviceTime"].toInt();
        d.status = o["status"].toString();
        d.createdAt = QDateTime::fromString(o["createdAt"].toString(), Qt::ISODate);
        return d;
    }
    QJsonObject toJson() const {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(id));
        o["taskId"] = QJsonValue::fromVariant(QVariant(taskId));
        o["pickupLocId"] = QJsonValue::fromVariant(QVariant(pickupLocId));
        o["deliveryLocId"] = QJsonValue::fromVariant(QVariant(deliveryLocId));
        o["timeStart"] = timeStart.toString(Qt::ISODate);
        o["timeEnd"] = timeEnd.toString(Qt::ISODate);
        o["revenue"] = revenue;
        o["weight"] = weight;
        o["volume"] = volume;
        o["serviceTime"] = serviceTime;
        o["status"] = status;
        return o;
    }
};

// ---------- Vehicle ----------
struct VehicleData {
    qulonglong id = 0;
    QString personId;
    QString status = "IDLE";
    qulonglong curLocId = 0;
    QDateTime curAvailableTime;
    QString toolType;
    double maxWeight = 0.0;
    double maxVolume = 0.0;
    double speed = 30.0;
    QDateTime shiftStart;
    QDateTime shiftEnd;
    double earnedRevenue = 0.0;

    static VehicleData fromJson(const QJsonObject &o) {
        VehicleData d;
        d.id = o["id"].toVariant().toULongLong();
        d.personId = o["personId"].toString();
        d.status = o["status"].toString();
        d.curLocId = o["curLocId"].toVariant().toULongLong();
        d.curAvailableTime = QDateTime::fromString(o["curAvailableTime"].toString(), Qt::ISODate);
        d.toolType = o["toolType"].toString();
        d.maxWeight = o["maxWeight"].toDouble();
        d.maxVolume = o["maxVolume"].toDouble();
        d.speed = o["speed"].toDouble();
        d.shiftStart = QDateTime::fromString(o["shiftStart"].toString(), Qt::ISODate);
        d.shiftEnd = QDateTime::fromString(o["shiftEnd"].toString(), Qt::ISODate);
        d.earnedRevenue = o["earnedRevenue"].toDouble();
        return d;
    }
    QJsonObject toJson() const {
        QJsonObject o;
        o["id"] = QJsonValue::fromVariant(QVariant(id));
        o["personId"] = personId;
        o["status"] = status;
        o["curLocId"] = QJsonValue::fromVariant(QVariant(curLocId));
        o["curAvailableTime"] = curAvailableTime.toString(Qt::ISODate);
        o["toolType"] = toolType;
        o["maxWeight"] = maxWeight;
        o["maxVolume"] = maxVolume;
        o["speed"] = speed;
        if (shiftStart.isValid()) o["shiftStart"] = shiftStart.toString(Qt::ISODate);
        if (shiftEnd.isValid()) o["shiftEnd"] = shiftEnd.toString(Qt::ISODate);
        o["earnedRevenue"] = earnedRevenue;
        return o;
    }
};

// ---------- Vehicle Type preset ----------
struct VehicleTypeData {
    QString name;
    double maxWeight = 0.0;
    double maxVolume = 0.0;
    double speed = 30.0;

    static VehicleTypeData fromJson(const QJsonObject &o) {
        VehicleTypeData d;
        d.name = o["name"].toString();
        d.maxWeight = o["maxWeight"].toDouble();
        d.maxVolume = o["maxVolume"].toDouble();
        d.speed = o["speed"].toDouble();
        return d;
    }
};

// ---------- Schedule result ----------
struct RouteLeg {
    int seq = 0;
    qulonglong orderId = 0;
    QDateTime plannedStart;
    QDateTime plannedEnd;
    int idleBefore = 0;
    double loadWeight = 0.0;
    double loadVolume = 0.0;
};

struct VehicleRoute {
    qulonglong vehicleId = 0;
    QList<RouteLeg> route;
};

struct ScheduleStats {
    int totalIdle = 0;
    double totalRevenue = 0.0;
    bool feasible = false;
};

struct ScheduleResult {
    QList<VehicleRoute> vehicles;
    QList<qulonglong> unassigned;
    ScheduleStats stats;

    static ScheduleResult fromJson(const QJsonObject &o) {
        ScheduleResult r;
        for (const auto &v : o["vehicles"].toArray()) {
            auto vo = v.toObject();
            VehicleRoute vr;
            vr.vehicleId = vo["vehicleId"].toVariant().toULongLong();
            for (const auto &leg : vo["route"].toArray()) {
                auto lo = leg.toObject();
                RouteLeg l;
                l.seq = lo["seq"].toInt();
                l.orderId = lo["orderId"].toVariant().toULongLong();
                l.plannedStart = QDateTime::fromString(lo["plannedStart"].toString(), Qt::ISODate);
                l.plannedEnd = QDateTime::fromString(lo["plannedEnd"].toString(), Qt::ISODate);
                l.idleBefore = lo["idleBefore"].toInt();
                l.loadWeight = lo["loadWeight"].toDouble();
                l.loadVolume = lo["loadVolume"].toDouble();
                vr.route.append(l);
            }
            r.vehicles.append(vr);
        }
        for (const auto &u : o["unassigned"].toArray()) {
            r.unassigned.append(u.toVariant().toULongLong());
        }
        auto s = o["stats"].toObject();
        r.stats.totalIdle = s["totalIdle"].toInt();
        r.stats.totalRevenue = s["totalRevenue"].toDouble();
        r.stats.feasible = s["feasible"].toBool();
        return r;
    }
};
