#pragma once

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <functional>

class ApiClient : public QObject {
    Q_OBJECT
public:
    explicit ApiClient(QObject *parent = nullptr);
    explicit ApiClient(const QString &baseUrl, QObject *parent = nullptr);

    void setBaseUrl(const QString &url) { m_baseUrl = url; }
    QString baseUrl() const { return m_baseUrl; }

    // Orders
    void getOrders(std::function<void(QList<QJsonObject>)> cb);
    void createOrder(const QJsonObject &order, std::function<void(QJsonObject)> cb);
    void createOrdersBatch(const QJsonArray &orders, std::function<void(QJsonArray)> cb);
    void deleteOrder(qulonglong id, std::function<void()> cb);
    void setOrderStatus(qulonglong id, const QString &status, std::function<void(QJsonObject)> cb);
    void getOrderVehicle(qulonglong id, std::function<void(QJsonObject)> cb);

    // Vehicles
    void getVehicles(std::function<void(QList<QJsonObject>)> cb);
    void getVehicleTypes(std::function<void(QList<QJsonObject>)> cb);
    void createVehicle(const QJsonObject &vehicle, std::function<void(QJsonObject)> cb);
    void createVehiclesBatch(const QJsonArray &vehicles, std::function<void(QJsonArray)> cb);
    void offlineVehicle(qulonglong id, std::function<void(QJsonObject)> cb);
    void setVehicleStatus(qulonglong id, const QString &status, std::function<void(QJsonObject)> cb);
    void deleteVehicle(qulonglong id, std::function<void()> cb);

    // Locations
    void getLocations(std::function<void(QList<QJsonObject>)> cb);
    void createLocation(const QJsonObject &location, std::function<void(QJsonObject)> cb);
    void createLocationsBatch(const QJsonArray &locations, std::function<void(QJsonArray)> cb);
    void geocode(const QString &address, std::function<void(QJsonObject)> cb);
    void deleteLocation(qulonglong id, std::function<void(bool success, const QString &err)> cb);

    // Schedule
    void triggerSchedule(std::function<void(QJsonObject)> cb);
    void dryRunSchedule(std::function<void(QJsonObject)> cb);
    void debugArcs(std::function<void(QJsonObject)> cb);
    void reschedule(std::function<void(QJsonObject)> cb);
    void dryRunDynamic(std::function<void(QJsonObject)> cb);
    void currentSchedule(std::function<void(QJsonObject)> cb);

    // Dynamic triggers
    void completeOrder(qulonglong vehicleId, qulonglong orderId, std::function<void()> cb);

    // Config (Map Key/SK)
    void getMapKeyConfig(std::function<void(QJsonObject)> cb);
    void saveMapKeyConfig(const QString &key, const QString &sk, std::function<void(QJsonObject)> cb);

private:
    QNetworkRequest makeRequest(const QString &path);
    void sendGet(const QString &path, std::function<void(QJsonDocument)> cb);
    void sendPost(const QString &path, const QJsonDocument &body, std::function<void(QJsonDocument)> cb);
    void sendDelete(const QString &path, std::function<void()> cb);

    QString m_baseUrl = "http://localhost:8080";
    QNetworkAccessManager *m_nam;
};
