#include <QApplication>
#include <QFont>
#include <QFontDatabase>
#include <QDebug>
#include "MainWindow.h"
#include "Theme.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    app.setApplicationName("物流调度系统");
    app.setOrganizationName("Logistics");

#ifdef __EMSCRIPTEN__
    QFont appFont("DejaVu Sans", 10);
#else
    QFont appFont("Microsoft YaHei", 10);
#endif
    appFont.setStyleStrategy(QFont::PreferAntialias);
    app.setFont(appFont);

    app.setStyleSheet(Theme::globalStyleSheet());

    MainWindow w;
    w.show();
    return app.exec();
}
