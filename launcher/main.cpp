#include <QProcess>
#include <QTimer>
#include <QThread>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>
#include <QDir>
#include <QFileInfo>
#include <QStandardPaths>
#include <QFile>
#include <QTextStream>
#include <QDateTime>
#include <iostream>
#include <cstdlib>

#ifdef Q_OS_WIN
#include <QApplication>
#include <QMessageBox>
#else
#include <QCoreApplication>
#endif

static const int    BACKEND_PORT    = 8080;
static const char*  BACKEND_JAR     = "scheduler-backend.jar";
static const char*  FRONTEND_BIN    = "logistics_ui";
static const int    STARTUP_TIMEOUT = 30000;

static QProcess* g_backend = nullptr;
static QFile*    g_logFile = nullptr;

// ── Logging: writes to both stdout and a log file ──
static void log(const QString& msg) {
    QString line = QString("[%1] %2").arg(QDateTime::currentDateTime().toString("HH:mm:ss.zzz"), msg);
    std::cout << line.toStdString() << std::endl;
    if (g_logFile && g_logFile->isOpen()) {
        QTextStream ts(g_logFile);
        ts << line << "\n";
        ts.flush();
    }
}

#ifdef Q_OS_WIN
static void fatalError(const QString& title, const QString& msg) {
    log(QString("FATAL: %1 - %2").arg(title, msg));
    QMessageBox::critical(nullptr, title, msg);
    if (g_logFile) g_logFile->close();
}
#else
static void fatalError(const QString& title, const QString& msg) {
    log(QString("FATAL: %1 - %2").arg(title, msg));
}
#endif

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
    log("Browser opened: " + url);
}

int main(int argc, char* argv[]) {
#ifdef Q_OS_WIN
    QApplication app(argc, argv);
#else
    QCoreApplication app(argc, argv);
#endif
    QCoreApplication::setApplicationName("logistics_launcher");
    QCoreApplication::setOrganizationName("Logistics");

    // ── 0. Open log file ──
    QString dataDir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    if (dataDir.isEmpty()) dataDir = QCoreApplication::applicationDirPath() + "/data";
    QDir().mkpath(dataDir);
    QString logPath = dataDir + "/launcher.log";
    g_logFile = new QFile(logPath, &app);
    if (g_logFile->open(QIODevice::WriteOnly | QIODevice::Append | QIODevice::Text)) {
        log("=== Launcher started ===");
    }

    // Parse CLI flags
    //   (default)      launch the native desktop client (logistics_ui)
    //   --browser      open the web UI in the default browser instead
    //   --desktop      force desktop client (same as default)
    //   --no-browser   don't open browser (kept for backward compatibility)
    bool openBrowserAfterStart = false;
    bool launchDesktopClient   = true;
    for (int i = 1; i < argc; ++i) {
        QString arg(argv[i]);
        if (arg == "--browser")    { launchDesktopClient = false; openBrowserAfterStart = true; }
        if (arg == "--no-browser") openBrowserAfterStart = false;
        if (arg == "--desktop")    { launchDesktopClient = true; openBrowserAfterStart = false; }
    }

    // ── 1. Start backend if not running ──
    if (!isBackendRunning()) {
        log("Starting backend...");
        QString jarPath = QCoreApplication::applicationDirPath() + "/" + BACKEND_JAR;
        if (!QFileInfo::exists(jarPath)) {
            fatalError("启动失败", QString("找不到后端文件:\n%1\n\n请确保程序完整。").arg(jarPath));
            return 1;
        }

        QString jrePath = findJre();
        if (!QFileInfo::exists(jrePath)) {
            fatalError("启动失败", QString("找不到 Java 运行时:\n%1").arg(jrePath));
            return 1;
        }
        log("JRE: " + jrePath);
        log("JAR: " + jarPath);

        QStringList jvmArgs;
        jvmArgs << "--enable-native-access=ALL-UNNAMED"
                << "-Ddb.path=" + dataDir + "/logistics"
                << "-jar" << jarPath;

        g_backend = new QProcess(&app);
        g_backend->setProcessChannelMode(QProcess::MergedChannels);
        g_backend->start(jrePath, jvmArgs);

        if (!g_backend->waitForStarted(10000)) {
            fatalError("启动失败", "后端进程启动失败，请查看日志文件:\n" + logPath);
            return 1;
        }

        int waited = 0;
        while (!isBackendRunning() && waited < STARTUP_TIMEOUT) {
            QThread::msleep(500);
            waited += 500;
        }
        if (waited >= STARTUP_TIMEOUT) {
            // Non-fatal: the backend process may still be starting up.
            // Launch the UI anyway so the user sees a window; the client
            // retries API calls on its own once the backend is ready.
            log("WARNING: Backend readiness probe timed out (30s). Backend output:");
            log(QString::fromLocal8Bit(g_backend->readAllStandardOutput()));
            log("Continuing to launch UI; backend may still come up.");
        } else {
            log(QString("Backend ready (http://localhost:%1)").arg(BACKEND_PORT));
        }
    } else {
        log("Backend already running.");
    }

    // ── 2. Launch UI ──
    QProcess* desktopClient = nullptr;
    if (launchDesktopClient) {
        log("Starting desktop client...");
        QString frontendPath = QCoreApplication::applicationDirPath() + "/" + FRONTEND_BIN;
#ifdef Q_OS_WIN
        frontendPath += ".exe";
#endif
        if (!QFileInfo::exists(frontendPath)) {
            fatalError("启动失败", QString("找不到界面程序:\n%1").arg(frontendPath));
            killBackend();
            return 1;
        }
        desktopClient = new QProcess(&app);
        desktopClient->setProcessChannelMode(QProcess::ForwardedChannels);
        QObject::connect(desktopClient, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            [&](int, QProcess::ExitStatus) { QCoreApplication::quit(); });
        desktopClient->start(frontendPath, QStringList());

        if (!desktopClient->waitForStarted(10000)) {
            fatalError("启动失败", QString("界面程序启动失败:\n%1").arg(frontendPath));
            killBackend();
            return 1;
        }
    } else if (openBrowserAfterStart) {
        openBrowser();
    }

    int ret = app.exec();

    // ── 3. Cleanup ──
    killBackend();
    log("Shutdown complete.");
    if (g_logFile) g_logFile->close();
    return ret;
}
