#include "ApiClient.h"
#include <QUrl>

ApiClient::ApiClient(QObject *parent) : QObject(parent) {
    m_nam = new QNetworkAccessManager(this);
}

ApiClient::ApiClient(const QString &baseUrl, QObject *parent)
    : QObject(parent), m_baseUrl(baseUrl) {
    m_nam = new QNetworkAccessManager(this);
}

QNetworkRequest ApiClient::makeRequest(const QString &path) {
    QNetworkRequest req(QUrl(m_baseUrl + path));
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    return req;
}

void ApiClient::sendGet(const QString &path, std::function<void(QJsonDocument)> cb) {
    QNetworkReply *reply = m_nam->get(makeRequest(path));
    connect(reply, &QNetworkReply::finished, this, [reply, cb]() {
        QByteArray data = reply->readAll();
        QJsonDocument doc = QJsonDocument::fromJson(data);
        cb(doc);
        reply->deleteLater();
    });
}

void ApiClient::sendPost(const QString &path, const QJsonDocument &body, std::function<void(QJsonDocument)> cb) {
    QNetworkReply *reply = m_nam->post(makeRequest(path), body.toJson());
    connect(reply, &QNetworkReply::finished, this, [reply, cb]() {
        QByteArray data = reply->readAll();
        QJsonDocument doc = QJsonDocument::fromJson(data);
        cb(doc);
        reply->deleteLater();
    });
}

void ApiClient::sendDelete(const QString &path, std::function<void()> cb) {
    QNetworkReply *reply = m_nam->deleteResource(makeRequest(path));
    connect(reply, &QNetworkReply::finished, this, [reply, cb]() {
        cb();
        reply->deleteLater();
    });
}

// 订单页
void ApiClient::getOrders(std::function<void(QList<QJsonObject>)> cb) {
    sendGet("/api/orders", [cb](QJsonDocument doc) {
        QList<QJsonObject> list;
        for (const auto &v : doc.array()) list.append(v.toObject());
        cb(list);
    });
}
void ApiClient::createOrder(const QJsonObject &order, std::function<void(QJsonObject)> cb) {
    sendPost("/api/orders", QJsonDocument(order), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::createOrdersBatch(const QJsonArray &orders, std::function<void(QJsonArray)> cb) {
    sendPost("/api/orders/batch", QJsonDocument(orders), [cb](QJsonDocument doc) { cb(doc.array()); });
}
void ApiClient::deleteOrder(qulonglong id, std::function<void()> cb) {
    sendDelete(QString("/api/orders/%1").arg(id), cb);
}
void ApiClient::setOrderStatus(qulonglong id, const QString &status, std::function<void(QJsonObject)> cb) {
    QJsonObject body;
    body["status"] = status;
    sendPost(QString("/api/orders/%1/status").arg(id), QJsonDocument(body),
             [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::getOrderVehicle(qulonglong id, std::function<void(QJsonObject)> cb) {
    sendGet(QString("/api/orders/%1/vehicle").arg(id), [cb](QJsonDocument doc) { cb(doc.object()); });
}

// 车辆页
void ApiClient::getVehicles(std::function<void(QList<QJsonObject>)> cb) {
    sendGet("/api/vehicles", [cb](QJsonDocument doc) {
        QList<QJsonObject> list;
        for (const auto &v : doc.array()) list.append(v.toObject());
        cb(list);
    });
}
void ApiClient::getVehicleTypes(std::function<void(QList<QJsonObject>)> cb) {
    sendGet("/api/vehicles/types", [cb](QJsonDocument doc) {
        QList<QJsonObject> list;
        for (const auto &v : doc.array()) list.append(v.toObject());
        cb(list);
    });
}
void ApiClient::createVehicle(const QJsonObject &vehicle, std::function<void(QJsonObject)> cb) {
    sendPost("/api/vehicles", QJsonDocument(vehicle), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::createVehiclesBatch(const QJsonArray &vehicles, std::function<void(QJsonArray)> cb) {
    sendPost("/api/vehicles/batch", QJsonDocument(vehicles), [cb](QJsonDocument doc) { cb(doc.array()); });
}
void ApiClient::offlineVehicle(qulonglong id, std::function<void(QJsonObject)> cb) {
    sendPost(QString("/api/vehicles/%1/offline").arg(id), QJsonDocument(), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::setVehicleStatus(qulonglong id, const QString &status, std::function<void(QJsonObject)> cb) {
    QJsonObject body;
    body["status"] = status;
    sendPost(QString("/api/vehicles/%1/status").arg(id), QJsonDocument(body),
             [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::deleteVehicle(qulonglong id, std::function<void()> cb) {
    sendDelete(QString("/api/vehicles/%1").arg(id), cb);
}

// 地址页
void ApiClient::getLocations(std::function<void(QList<QJsonObject>)> cb) {
    sendGet("/api/locations", [cb](QJsonDocument doc) {
        QList<QJsonObject> list;
        for (const auto &v : doc.array()) list.append(v.toObject());
        cb(list);
    });
}
void ApiClient::createLocation(const QJsonObject &location, std::function<void(QJsonObject)> cb) {
    sendPost("/api/locations", QJsonDocument(location), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::createLocationsBatch(const QJsonArray &locations, std::function<void(QJsonArray)> cb) {
    sendPost("/api/locations/batch", QJsonDocument(locations), [cb](QJsonDocument doc) { cb(doc.array()); });
}

void ApiClient::geocode(const QString &address, std::function<void(QJsonObject)> cb) {
    QJsonObject body;
    body["address"] = address;
    sendPost("/api/locations/geocode", QJsonDocument(body), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::deleteLocation(qulonglong id, std::function<void(bool, const QString &)> cb) {
    QNetworkReply *reply = m_nam->deleteResource(makeRequest(QString("/api/locations/%1").arg(id)));
    connect(reply, &QNetworkReply::finished, this, [reply, cb]() {
        int code = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        if (code >= 200 && code < 300) {
            cb(true, {});
        } else {
            QString err = QString::fromUtf8(reply->readAll());
            if (err.isEmpty()) err = QString("HTTP %1").arg(code);
            cb(false, err);
        }
        reply->deleteLater();
    });
}

// 调度页
void ApiClient::triggerSchedule(std::function<void(QJsonObject)> cb) {
    sendPost("/api/schedule", QJsonDocument(), [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::dryRunSchedule(std::function<void(QJsonObject)> cb) {
    sendGet("/api/schedule/dry-run", [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::debugArcs(std::function<void(QJsonObject)> cb) {
    sendGet("/api/schedule/debug/arcs", [cb](QJsonDocument doc) { cb(doc.object()); });
}

void ApiClient::reschedule(std::function<void(QJsonObject)> cb) {
    sendPost("/api/schedule/reschedule", QJsonDocument(), [cb](QJsonDocument doc) { cb(doc.object()); });
}

void ApiClient::dryRunDynamic(std::function<void(QJsonObject)> cb) {
    sendGet("/api/schedule/dry-run-dynamic", [cb](QJsonDocument doc) { cb(doc.object()); });
}

void ApiClient::currentSchedule(std::function<void(QJsonObject)> cb) {
    sendGet("/api/schedule/current", [cb](QJsonDocument doc) { cb(doc.object()); });
}

void ApiClient::completeOrder(qulonglong vehicleId, qulonglong orderId, std::function<void()> cb) {
    sendPost(QString("/api/vehicles/%1/complete?orderId=%2").arg(vehicleId).arg(orderId),
             QJsonDocument(), [cb](QJsonDocument) { cb(); });
}

// Config
void ApiClient::getMapKeyConfig(std::function<void(QJsonObject)> cb) {
    sendGet("/api/config/mapkey", [cb](QJsonDocument doc) { cb(doc.object()); });
}
void ApiClient::saveMapKeyConfig(const QString &key, const QString &sk, std::function<void(QJsonObject)> cb) {
    QJsonObject body;
    body["key"] = key;
    body["sk"] = sk;
    sendPost("/api/config/mapkey", QJsonDocument(body), [cb](QJsonDocument doc) { cb(doc.object()); });
}
