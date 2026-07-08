#include <QProcess>
#include <QCoreApplication>
#include <QTimer>
#include <QThread>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>
#include <QDir>
#include <QFileInfo>
#include <QStandardPaths>
#include <iostream>
#include <cstdlib>

static const int    BACKEND_PORT    = 8080;
static const char*  BACKEND_JAR     = "scheduler-backend.jar";
static const char*  FRONTEND_BIN    = "logistics_ui";
static const int    STARTUP_TIMEOUT = 30000;

static QProcess* g_backend = nullptr;

static QString findJre() {
    QStringList paths;
    paths << QCoreApplication::applicationDirPath() + "/jre/bin/java";
#ifdef Q_OS_WIN
    paths << QCoreApplication::applicationDirPath() + "/jre/bin/java.exe";
#endif
    paths << "java";
    for (const auto& p : paths) {
        if (QFileInfo::exists(p)) return p;
    }
    return "java";
}

static bool isBackendRunning() {
    QNetworkAccessManager mgr;
    QNetworkRequest req(QUrl(QString("http://localhost:%1/api/locations").arg(BACKEND_PORT)));
    QNetworkReply* reply = mgr.get(req);
    QTimer timer;
    timer.setSingleShot(true);
    QEventLoop loop;
    bool ok = false;
    QObject::connect(reply, &QNetworkReply::finished, [&]() {
        ok = (reply->error() == QNetworkReply::NoError);
        reply->deleteLater();
        loop.quit();
    });
    QObject::connect(&timer, &QTimer::timeout, [&]() {
        reply->deleteLater();
        loop.quit();
    });
    timer.start(2000);
    loop.exec();
    return ok;
}

static void killBackend() {
    if (g_backend && g_backend->state() != QProcess::NotRunning) {
        g_backend->terminate();
        if (!g_backend->waitForFinished(5000))
            g_backend->kill();
        delete g_backend;
        g_backend = nullptr;
    }
}

static void openBrowser() {
    QString url = QString("http://localhost:%1").arg(BACKEND_PORT);
#ifdef Q_OS_WIN
        QProcess::startDetached("cmd", QStringList() << "/c" << "start" << url);
#else
        QProcess::startDetached("xdg-open", QStringList() << url);
#endif
    std::cout << "[launcher] Browser opened: " << url.toStdString() << std::endl;
}

int main(int argc, char* argv[]) {
    QCoreApplication app(argc, argv);
    QCoreApplication::setApplicationName("logistics_launcher");

    // Parse CLI flags
    // --no-browser: don't auto-open browser (for desktop Qt client users)
    // --desktop:    launch the Qt desktop client instead of opening browser
    bool openBrowserAfterStart = true;
    bool launchDesktopClient   = false;
    for (int i = 1; i < argc; ++i) {
        QString arg(argv[i]);
        if (arg == "--no-browser") openBrowserAfterStart = false;
        if (arg == "--desktop")    { launchDesktopClient = true; openBrowserAfterStart = false; }
    }

    // ── 1. Ensure data dir exists ──
    QString dataDir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    if (dataDir.isEmpty()) dataDir = QCoreApplication::applicationDirPath() + "/data";
    QDir().mkpath(dataDir);

    // ── 2. Start backend if not running ──
    if (!isBackendRunning()) {
        std::cout << "[launcher] Starting backend..." << std::endl;
        QString jarPath = QCoreApplication::applicationDirPath() + "/" + BACKEND_JAR;
        if (!QFileInfo::exists(jarPath)) {
            std::cerr << "[launcher] ERROR: " << jarPath.toStdString() << " not found" << std::endl;
            return 1;
        }

        QStringList jvmArgs;
        jvmArgs << "--enable-native-access=ALL-UNNAMED"
                << "-Ddb.path=" + dataDir + "/logistics"
                << "-jar" << jarPath;

        g_backend = new QProcess(&app);
        g_backend->setProcessChannelMode(QProcess::ForwardedChannels);
        g_backend->start(findJre(), jvmArgs);

        if (!g_backend->waitForStarted(10000)) {
            std::cerr << "[launcher] Failed to start backend." << std::endl;
            return 1;
        }

        int waited = 0;
        while (!isBackendRunning() && waited < STARTUP_TIMEOUT) {
            QThread::msleep(500);
            waited += 500;
        }
        if (waited >= STARTUP_TIMEOUT) {
            std::cerr << "[launcher] Backend startup timeout." << std::endl;
            killBackend();
            return 1;
        }
        std::cout << "[launcher] Backend ready (http://localhost:" << BACKEND_PORT << ")" << std::endl;
    } else {
        std::cout << "[launcher] Backend already running." << std::endl;
    }

    // ── 3. Launch UI ──
    QProcess* desktopClient = nullptr;
    if (launchDesktopClient) {
        std::cout << "[launcher] Starting desktop client..." << std::endl;
        QString frontendPath = QCoreApplication::applicationDirPath() + "/" + FRONTEND_BIN;
#ifdef Q_OS_WIN
        frontendPath += ".exe";
#endif
        desktopClient = new QProcess(&app);
        desktopClient->setProcessChannelMode(QProcess::ForwardedChannels);
        QObject::connect(desktopClient, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            [&]() { QCoreApplication::quit(); });
        desktopClient->start(frontendPath, QStringList());

        if (!desktopClient->waitForStarted(10000)) {
            std::cerr << "[launcher] Failed to start desktop client: " << frontendPath.toStdString() << std::endl;
            killBackend();
            return 1;
        }
    } else if (openBrowserAfterStart) {
        openBrowser();
    }

    int ret = app.exec();

    // ── 4. Cleanup ──
    killBackend();
    std::cout << "[launcher] Shutdown complete." << std::endl;
    return ret;
}

