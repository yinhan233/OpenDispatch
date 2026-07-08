# 开放车辆调度软件 
## 题设 
> **项目类型**：C++ / Java 开发　|　**难度**：A　|　**需要学生数**：3
>
> **项目内容及要求**
>
> “自由快递人”等虚拟物流配送模式逐渐发展，不靠自有车辆和员工，而是吸引物流需求点附近的社会兼职车辆和人员参与，将物流收益分享给参与的社会个体。这给车辆调度问题带来新的挑战，调度目标发生了变化，开始考虑每个个体车辆的利益和均衡，以尽可能优化吸引社会闲置或兼职物流资源，满足物流需求。
>
> 对运输户来说，车辆闲置等同于亏损，所以，本项目以**车辆总闲置时间最短**为目标，闲置时间计算为车辆在路线中相邻两单之间的累计空闲时间。把每个订单尾部看作闲置起始点，头部看作闲置终点，根据需求时间窗和路线时间长度信息，计算出任意两个订单若相邻将会产生的车辆闲置时间，则问题转化为要用最少时间完成这些闲置任务，即**最小费用流问题**。采用经典算法求出它的精确解，以此构成一个车辆调度模型和相应算法。
>
> 基本功能：输入物流任务和车辆信息、计算最优调度方案、图形化输出调度方案。完成基本功能后，可选添加动画界面，以及对功能和细节的进一步完善。

---

## 项目简介

本系统面向“自由快递人”式的众包物流场景，帮助调度者把一批带时间窗的订单分配给若干社会车辆，
并给出**总闲置时间最短**的调度方案。

核心思路是把调度问题建模为**最小费用流**：将每个订单抽象为一条“订单弧”，
两订单若相邻则产生一段车辆闲置时间，作为费用；再用连续最短路（SSP + Dijkstra + 势函数，支持下界）
求出精确最优解。结果以**甘特图 / 地图 / 动画**三种方式图形化呈现。

系统采用 **Java 后端 + Qt 前端** 的分离式架构：

- **后端**：Java + Javalin，提供 REST API，内置调度算法与数据库访问（默认 H2 单机，可切换 MySQL）。
- **前端**：Qt6 C++ 客户端，同一套代码可编译为 **原生桌面程序** 或 **WebAssembly 网页版**。
- **启动器**：C++ 编写的 launcher，负责拉起后端 JAR 并打开桌面客户端或浏览器，实现“单文件即开即用”。

## 项目技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Javalin 6、Jackson、HikariCP |
| 数据库 | H2（单机默认）/ MySQL（可选），JDBC |
| 调度算法 | 最小费用流（SSP + Dijkstra + 势函数 + 下界） |
| 前端 | Qt 6.8（Widgets / Network），C++17，可编译为原生或 WASM |
| 打包 | Maven（mvnw）、CMake、AppImage（Linux）、windeployqt（Windows）、jlink（精简 JRE） |

## 如何使用

### 直接运行（推荐）

我们会把编译好的产物上传到 [Releases](../../releases)：

- **Windows**：下载 `windows` 压缩包，解压后双击 `logistics_launcher_only.exe`。
- **Linux**：下载 `LogisticsManager-x86_64.AppImage`，赋予可执行权限后运行：

  ```bash
  chmod +x LogisticsManager-x86_64.AppImage
  ./LogisticsManager-x86_64.AppImage
  ```

启动器会自动运行内置后端（端口 `8080`）并打开界面。默认使用 H2 数据库，数据保存在运行目录的 `data/` 下，无需额外配置。
若要接入 MySQL，可通过环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` 指定。

### 从源码编译

现有编译脚本 `package.sh`，可产出 Linux AppImage 与 Windows 发行版。

**一次性环境准备**（详见脚本头部注释）：

```bash
# Qt6（桌面 + WASM 目标 + Linux host 工具）
pip install aqtinstall
aqt install-qt linux  desktop 6.8.0 linux_gcc_64      -O ~/Qt
aqt install-qt windows desktop 6.8.0 win64_mingw       -O ~/Qt   # 交叉编译 Windows 用
aqt install-qt all_os  wasm    6.8.0 wasm_singlethread -O ~/Qt   # 网页版用

# Emscripten（编译 WASM 前端）
git clone --depth=1 https://github.com/emscripten-core/emsdk ~/emsdk
~/emsdk/emsdk install 3.1.56 && ~/emsdk/emsdk activate 3.1.56

# Windows 交叉编译器（须为 msvcrt 版！见下方注意事项）
# Arch: yay -S mingw-w64-headers-msvcrt mingw-w64-crt-msvcrt \
#              mingw-w64-gcc-msvcrt mingw-w64-winpthreads-msvcrt

# 还需要本机安装 JDK 17+（用于 jlink 生成精简 JRE）与 CMake
```

> **Windows 交叉编译注意事项**：aqt 下载的 Qt 6.8（win64_mingw）编译时链接的是 **msvcrt**（旧版 Microsoft C 运行时）。若用系统默认的 **UCRT** 版 MinGW-w64 工具链交叉编译，产出的 exe 会链接 api-ms-win-crt（新版运行时），与 Qt 的 msvcrt 堆不互通，运行即 STATUS_HEAP_CORRUPTION（0xC0000374）崩溃。因此须安装 **msvcrt 版 MinGW-w64**（如 Arch 的 `mingw-w64-gcc-msvcrt` 等包），它与系统默认的 UCRT 版互相冲突、只能二选一。Linux 原生编译与 WASM 不受此影响。

**执行打包**：

```bash
./package.sh linux      # 仅构建 Linux AppImage
./package.sh windows    # 交叉编译 Windows 发行版
./package.sh all        # 全部构建
./package.sh            # 交互式选择
```

打包流程会依次：编译 WASM 前端并嵌入后端资源 → 用 `mvnw` 打出后端 uber-jar → 编译原生 Qt 客户端与
launcher → 生成精简 JRE → 组装为 AppImage / Windows 目录。产物输出到 `dist/`。

**只编译单个模块调试**：

```bash
# 后端
cd server && ./mvnw package -DskipTests
java -jar target/scheduler-backend-*.jar        # 浏览器访问 http://localhost:8080

# Qt 桌面客户端
cd qtclient && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j
```

## 设计理念

- **算法与业务解耦**：调度核心是纯粹的最小费用流求解器（`server/.../algorithm/`），
  与订单、车辆、时间窗等业务概念通过 `ArcBuilder` 建图隔离，算法可独立测试（见 `MinCostFlowTest`）。
- **一份前端，两种形态**：Qt6 同一套 Widgets 代码既可编译为原生桌面程序，也可编译为 WASM 网页版，
  网页版被打进后端 JAR，实现“打开即用、无需安装”。
- **零配置起步、可平滑升级**：默认用嵌入式 H2 单机数据库开箱即用；接入 MySQL 只需设置环境变量，
  代码无需改动，兼顾课程演示与真实部署。
- **单文件分发**：launcher + 后端 JAR + 前端 + 精简 JRE 一起打包成 AppImage / exe，
  用户无需预装 Java 或 Qt 运行库。
