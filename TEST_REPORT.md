# 物流调度系统 — 测试报告

> 项目：开放车辆调度软件（Java 后端 + Qt 前端）
> 测试日期：2026-07-09
> 测试环境：Linux x86_64, JDK 17, Qt 6.8.0, H2 (内存库)

---

## 一、测试总览

| 层次 | 测试类型 | 测试类别 | 测试项数 | 通过 | 失败 |
|------|---------|---------|---------|------|------|
| 后端 | 静态测试 | 代码规范 (Checkstyle) | 配置 | — | — |
| 后端 | 动态测试 | 白盒 — 算法层 | 26 | 26 | 0 |
| 后端 | 动态测试 | 白盒 — DAO 层 | 31 | 31 | 0 |
| 后端 | 动态测试 | 白盒 — Service 层 | 8 | 8 | 0 |
| 后端 | 动态测试 | 黑盒 — REST API | 45 | 45 | 0 |
| 后端 | 动态测试 | 集成测试 — 端到端 | 1 | 1 | 0 |
| 前端 | 静态测试 | 代码规范 (clang-tidy) | 配置 | — | — |
| 前端 | 动态测试 | 白盒 — Model 层 | 15 | 15 | 0 |
| 前端 | 动态测试 | 白盒 — ApiClient | 20 | 20 | 0 |
| 前端 | 动态测试 | 白盒 — Theme | 7 | 7 | 0 |
| 前端 | 动态测试 | 白盒 — GanttChart | 9 | 9 | 0 |
| 前端 | 动态测试 | 白盒 — MapWidget | 8 | 8 | 0 |
| **合计** | | | **171** | **171** | **0** |

---

## 二、静态测试

### 2.1 后端 — Checkstyle 代码规范检查

**配置文件：** `server/checkstyle.xml`

| 规则类别 | 具体规则 | 级别 |
|---------|---------|------|
| 缩进 | 4 空格，禁止 Tab | Error |
| 行长度 | ≤120 字符 | Error |
| 命名规范 | 类名 CamelCase，方法/变量 lowerCamelCase | Error |
| 导入 | 禁止通配符 `import *` | Error |
| 空白 | 禁止行尾空白 | Error |
| 异常 | throws 子句 ≤3 个异常类型 | Warning |
| 方法长度 | ≤80 行 | Warning |
| 圈复杂度 | ≤10 (Warning) / ≤15 (Error) | Warning/Error |
| 编码 | UTF-8 | Error |

**审查范围：**
- 算法正确性：最小费用流 SSP + Dijkstra + 势函数逻辑
- 安全性：SQL 参数化查询防注入，无硬编码密钥
- 异常处理：全局 ExceptionHandler，DAO 层 SQLException 传递
- 并发：HikariCP 连接池线程安全

### 2.2 前端 — clang-tidy 静态分析

**配置文件：** `qtclient/.clang-tidy`

| 规则组 | 数量 | 说明 |
|--------|------|------|
| bugprone-* | 全开（1项排除） | 常见 Bug 检查 |
| cppcoreguidelines-* | 全开（6项排除） | C++ Core Guidelines |
| modernize-* | 全开（1项排除） | 现代化 C++17 实践 |
| performance-* | 全开 | 性能优化建议 |
| readability-* | 全开（2项排除） | 可读性检查 |
| qt-* | 全开 | Qt 最佳实践 |

**审查范围：**
- 内存管理：堆/栈对象生命周期，parent-child 机制
- 信号槽：connect 配对正确性
- JSON 序列化：空字段处理，null 值安全
- 命名：`m_` 前缀成员变量

---

## 三、后端动态测试

### 3.1 算法层 — 白盒测试（26 项）

#### MinCostFlowTest（已有，3 项）— 语句覆盖

| # | 测试用例 | 测试方法 | 覆盖路径 |
|---|---------|---------|---------|
| 1 | `singleVehicleTwoOrdersInSequence` | 基本流 | SSP→Dijkstra→增广→extractSolution |
| 2 | `unassignedWhenNoFeasibleVehicle` | 兜底边 | fallback S→R_j 路径 |
| 3 | `twoVehiclesPickCheaperIdle` | 最优分配 | 费用比较路径 |

#### ArcBuilderWhiteBoxTest（15 项）

| # | 测试用例 | 测试方法 | 覆盖内容 |
|---|---------|---------|---------|
| 1 | `singleOrderSingleVehicle` | 语句覆盖 | ArcBuilder#build 正常路径 |
| 2 | `orderExceedsVehicleWeight` | 分支覆盖 | 重量约束 > maxWeight 分支 |
| 3 | `orderExceedsVehicleVolume` | 分支覆盖 | 容积约束 > maxVolume 分支 |
| 4 | `orderMissesTimeWindow` | 分支覆盖 | actualEnd > bSec 分支 |
| 5 | `multipleOrdersChain` | 路径覆盖 | 续接边 L_i→R_j 路径 |
| 6 | `noVehiclesAvailable` | 边界值 | 车辆数=0 |
| 7 | `noOrdersToSchedule` | 边界值 | 订单数=0 |
| 8 | `veryLargeNetwork` | 压力测试 | 10车×10单=100+弧 |
| 9 | `sameLocationOrderAndVehicle` | 边界值 | idle=0（车辆在取货点） |
| 10 | `tightTimeWindow` | 边界值 | 时间窗刚好够 |
| 11 | `orderAtVehicleCapacityLimit` | 边界值 | 订单重量=车辆容量 |
| 12 | `negativeBaseSegment` | 等价类 | 负 baseSegment |
| 13 | `vehicleShiftTimeRestriction` | 分支覆盖 | shift 时间外订单不可分配 |
| 14 | `buildResultNodeCount` | 语句覆盖 | nodeCount = V+2n+2 公式验证 |
| 15 | `arcKindsCovered` | 语句覆盖 | 所有 Arc.Kind 值覆盖 |

#### MinCostFlowExtendedTest（8 项）

| # | 测试用例 | 测试方法 | 覆盖内容 |
|---|---------|---------|---------|
| 1 | `solveWithLowerBoundsSingleEdge` | 路径覆盖 | solveWithLowerBounds 两阶段路径 |
| 2 | `solveWithLowerBoundsInfeasible` | 分支覆盖 | 下界不可行 → feasible=false |
| 3 | `emptyGraphSolve` | 边界值 | demand=0 空图 |
| 4 | `singleEdgeSimple` | 语句覆盖 | 基本 S→T 单边流 |
| 5 | `negativeDemand` | 边界值 | demand=0,图非空 |
| 6 | `multiplePathsChooseCheaper` | 路径选择 | 两路径选费用低的 |
| 7 | `largeCapacityEdge` | 边界值 | Long.MAX_VALUE/4 容量 |
| 8 | `zeroCostCycle` | 等价类 | 零费用环不影响结果 |

### 3.2 DAO 层 — 白盒测试（31 项）

所有 DAO 测试基于 H2 内存数据库，`@BeforeEach` 截断表并重置自增。

#### OrderDaoTest（10 项）

| # | 测试用例 | 测试方法 | 覆盖 |
|---|---------|---------|------|
| 1 | `insertAndFindById` | 语句覆盖 | insert→findById 完整路径 |
| 2 | `findAllMultiple` | 分支覆盖 | 多行 ResultSet 遍历 |
| 3 | `findByStatus` | 分支覆盖 | WHERE 条件分支 |
| 4 | `updateStatus` | 语句覆盖 | UPDATE 执行 |
| 5 | `updateStatusBatch` | 等价类 | 正常批量更新 |
| 6 | `updateStatusBatchEmpty` | 边界值 | 空列表不抛异常 |
| 7 | `findByIds` | 等价类 | 批量 IN 查询 |
| 8 | `findByIdsEmpty` | 边界值 | 空列表返回 [] |
| 9 | `findByNonExistentId` | 等价类 | 无效 ID→null |
| 10 | `deleteCascading` | 路径覆盖 | 级联删除 route_item + unassigned_order |

#### VehicleDaoTest（9 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `insertAndFindById` | 语句覆盖 |
| 2 | `findAll` | 等价类（多记录） |
| 3 | `findByStatus` | 分支覆盖 |
| 4 | `updateStatusAndLocation` | 多字段更新路径 |
| 5 | `findRouteIdsByVehicle` | 边界值（无路线） |
| 6 | `findByNonExistentId` | 等价类（无效ID） |
| 7 | `insertWithNullShift` | 等价类（null 班次） |
| 8 | `insertWithShiftTimes` | 等价类（非null 班次） |
| 9 | `insertWithNegativeSpeed` | 等价类（负速度） |

#### LocationDaoTest（6 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `insertAndFindById` | 语句覆盖 |
| 2 | `findAllIncludesDefault` | 分支覆盖（含 ID=0） |
| 3 | `findByNonExistent` | 边界值 |
| 4 | `maxId` | 聚合函数路径 |
| 5 | `deleteCascadesDistance` | 级联路径：location→distance→order→route_item |
| 6 | `insertWithAllFields` | 语句覆盖 |

#### RouteDaoTest（6 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `insertAndFindByVehicleId` | 语句覆盖 |
| 2 | `findByStatus` | 分支覆盖 |
| 3 | `findByStatusIn` | 多状态 IN 查询 |
| 4 | `findLatestByVehicleId` | ORDER BY + LIMIT |
| 5 | `updateStatus` | 语句覆盖 |
| 6 | `deleteById` | 语句覆盖 |

### 3.3 Service 层 — 白盒测试（8 项）

| # | 测试用例 | 测试方法 | 覆盖 |
|---|---------|---------|------|
| 1 | `scheduleNoData` | 边界值 | 0订单+0车辆→空结果 feasible=true |
| 2 | `scheduleOneOrderNoVehicle` | 等价类 | 1订单+0车辆→unassigned |
| 3 | `scheduleOneVehicleNoOrder` | 等价类 | 1车辆+0订单→空结果 |
| 4 | `scheduleOneOrderOneVehicleFeasible` | 语句覆盖 | 完整调度→分配→路线生成 |
| 5 | `scheduleOrderExceedsCapacity` | 分支覆盖 | 重量超限→unassigned |
| 6 | `persistThenGetCurrent` | 路径覆盖 | schedule→persist→getCurrent→数据一致 |
| 7 | `completeOrderFlow` | 路径覆盖 | schedule→complete→order DONE→vehicle IDLE |
| 8 | `buildOnlyHasArcs` | 语句覆盖 | buildOnly 返回弧列表 |

### 3.4 REST API — 黑盒测试（45 项）

覆盖全部 27 个端点，使用`等价类划分`、`边界值分析`、`错误推测`法。

#### 订单 API（8 端点 — 17 项）

| # | 端点 | 测试用例 | 等价类/边界值 |
|---|------|---------|-------------|
| 1 | GET /api/orders | 空库返回 [] | 边界值：空数据 |
| 2 | GET /api/orders | 2 订单返回 2 项 | 等价类：非空 |
| 3 | GET /api/orders/status/{s} | 按状态过滤 | 等价类：有匹配 |
| 4 | GET /api/orders/status/{s} | 无匹配返回 [] | 边界值：无匹配 |
| 5 | POST /api/orders | 有效 JSON 创建成功 | 等价类：有效输入 |
| 6 | POST /api/orders | 空 body | 等价类：无效输入 |
| 7 | POST /api/orders | 负 revenue | 等价类：无效输入 |
| 8 | POST /api/orders | timeStart=timeEnd | 边界值：即时订单 |
| 9 | POST /api/orders/batch | 批量创建 3 项 | 等价类：批量正常 |
| 10 | GET /api/orders/{id} | 有效 ID→200 | 等价类：有效 ID |
| 11 | GET /api/orders/{id} | 不存在→404 | 等价类：无效 ID |
| 12 | POST /api/orders/{id}/status | 有效状态→200 | 等价类：有效状态 |
| 13 | POST /api/orders/{id}/status | "INVALID"→400 | 等价类：无效状态 |
| 14 | POST /api/orders/{id}/status | 缺少 status 字段→400 | 等价类：缺少必填 |
| 15 | GET /api/orders/{id}/vehicle | 未分配→assigned=false | 等价类：未分配 |
| 16 | DELETE /api/orders/{id} | 删除成功→204 | 等价类：正常删除 |
| 17 | DELETE /api/orders/{id} | 不存在→异常处理 | 等价类：无效 ID 删除 |

#### 车辆 API（10 端点 — 9 项）

| # | 测试用例 | 等价类/边界值 |
|---|---------|-------------|
| 18 | 获取预设车型 8 种 | 语句覆盖 |
| 19 | 空库返回 [] | 边界值 |
| 20 | 创建车辆成功 | 等价类：有效输入 |
| 21 | 最少字段创建（自动填充 personId/curLocId） | 等价类：默认值 |
| 22 | 批量创建 | 等价类：批量正常 |
| 23 | 不存在的 ID→404 | 等价类：无效 ID |
| 24 | 非法状态 "BROKEN"→400 | 等价类：无效状态 |
| 25 | 下线操作 status→OFFLINE | 等价类：下线操作 |
| 26 | 删除后 204 | 等价类：正常删除 |

#### 地点 API（6 端点 — 6 项）

| # | 测试用例 | 等价类/边界值 |
|---|---------|-------------|
| 27 | 含默认地点 ID=0 | 语句覆盖 |
| 28 | 创建正常地点 | 等价类：有效输入 |
| 29 | lng=±180, lat=±90 | 边界值：极端坐标 |
| 30 | 空地址→400 | 等价类：无效输入 |
| 31 | 地址<3字符→400 | 边界值：过短地址 |
| 32 | 删除 ID=0→400 | 等价类：保护默认地点 |

#### 调度 API（6 端点 — 9 项）

| # | 测试用例 | 等价类/边界值 |
|---|---------|-------------|
| 33 | 空数据调度→feasible=true | 边界值：空数据 |
| 34 | 仅有订单无车辆→unassigned | 等价类：无车辆 |
| 35 | 正常 2 车 2 单→分配成功 | 等价类：正常调度 |
| 36 | dry-run 不落库 | 黑盒：验证副作用 |
| 37 | current 空 → 空结果 | 边界值：无持久化 |
| 38 | schedule 后 current 返回一致 | 等价类：持久化一致性 |
| 39 | debugArcs 返回弧列表 | 语句覆盖 |
| 40 | reschedule 空数据处理 | 边界值 |
| 41 | dry-run-dynamic 空数据处理 | 边界值 |

#### 配置 API（2 端点 — 4 项）

| # | 测试用例 | 等价类/边界值 |
|---|---------|-------------|
| 42 | 读取配置 configured=false | 等价类：默认配置 |
| 43 | 保存缺少 key→400 | 等价类：缺少必填 |
| 44 | 仅 key 无 sk→ok | 等价类：可选字段 |
| 45 | 完整 key+sk→ok | 等价类：完整配置 |

### 3.5 端到端集成测试（1 项）

`EndToEndTest.fullWorkflow` — 完整业务流程：

```
步骤1: POST /api/locations ×3   → 创建地点
步骤2: POST /api/vehicles  ×2   → 创建车辆
步骤3: POST /api/orders    ×5   → 创建订单
步骤4: GET  /api/orders         → 验证 5 个订单
步骤5: GET  /api/vehicles       → 验证 2 辆车
步骤6: POST /api/schedule       → 执行调度，验证车辆分配
步骤7: GET  /api/schedule/current → 验证持久化一致性
步骤8: GET  /api/orders/{id}/vehicle → 验证分配关系
步骤9: POST /api/vehicles/{id}/complete → 完成第一单
步骤10: GET /api/orders/{id}    → 验证状态 DONE
步骤11: POST /api/schedule/reschedule  → 动态重调度
步骤12: 验证整体数据一致性
```

---

## 四、前端动态测试（59 项）

### 4.1 Model 层 JSON 序列化（15 项）

| # | 测试用例 | 测试方法 | 覆盖 |
|---|---------|---------|------|
| 1 | `testLocationFromJson` | 等价类 | 完整 Location JSON→struct |
| 2 | `testLocationToJson` | 语句覆盖 | LocationData→JsonObject |
| 3 | `testLocationRoundtrip` | 数据完整性 | toJson→fromJson 往返一致 |
| 4 | `testLocationEmptyJson` | 边界值 | 空 JSON→默认值 |
| 5 | `testLocationPartialJson` | 等价类 | 部分字段：仅 id/lng |
| 6 | `testOrderFromJson` | 等价类 | 完整 OrderData（含 QDateTime） |
| 7 | `testOrderToJson` | 语句覆盖 | 含时间窗序列化 |
| 8 | `testOrderDefaultStatus` | 等价类 | 默认 status="UNASSIGNED" |
| 9 | `testVehicleFromJson` | 等价类 | Vehicle 含 shift 时间 |
| 10 | `testVehicleNullShift` | 边界值 | 无 shift 时间 |
| 11 | `testVehicleTypeFromJson` | 语句覆盖 | 车辆类型预设 |
| 12 | `testScheduleResultFromJson` | 等价类 | 完整调度结果结构 |
| 13 | `testScheduleResultEmpty` | 边界值 | 空调度结果 |
| 14 | `testOrderNegativeRevenue` | 等价类 | 负收益值 |
| 15 | `testLocationExtremeCoords` | 边界值 | lng=180, lat=90 |

### 4.2 Theme 样式表测试（7 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `testStyleSheetNotEmpty` | 语句覆盖（长度>100） |
| 2 | `testContainsSelectors` | QPushButton/QTableWidget/QLineEdit/QLabel |
| 3 | `testContainsCssClasses` | cssClass="secondary"/"danger"/"success" |
| 4 | `testPaletteDefined` | Canvas/Accent/Danger 颜色有效 |
| 5 | `testContainsTabWidget` | QTabWidget 样式存在 |
| 6 | `testContainsScrollBar` | QScrollBar 样式存在 |
| 7 | `testOrderColor` | Data1~Data8 订单颜色存在 |

### 4.3 ApiClient 测试（20 项）

| 类别 | 测试项数 | 内容 |
|------|---------|------|
| 构造/基础 URL | 2 | 默认 localhost:8080，setBaseUrl |
| Orders API 冒烟 | 5 | getOrders/createOrder/deleteOrder/setOrderStatus/getOrderVehicle |
| Vehicles API 冒烟 | 4 | getVehicles/getVehicleTypes/offlineVehicle/setVehicleStatus |
| Locations API 冒烟 | 3 | getLocations/createLocation/deleteLocation |
| Schedule API 冒烟 | 4 | triggerSchedule/dryRun/reschedule/current |
| Config API 冒烟 | 2 | getMapKeyConfig/saveMapKeyConfig |

### 4.4 GanttChart 组件测试（9 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `testDefaultState` | 默认最小尺寸≥600×300 |
| 2 | `testSetEmptySchedule` | 边界值：空调度不崩溃 |
| 3 | `testSetSingleVehicle` | 等价类：1车1段 |
| 4 | `testSetMultipleVehicles` | 等价类：3车2段→5车2段高度递增 |
| 5 | `testSetWithUnassigned` | 等价类：含未分配订单 |
| 6 | `testClear` | 语句覆盖：set后clear恢复默认 |
| 7 | `testSizeHint` | sizeHint≥600×300 |
| 8 | `testMinimumSizeHint` | minimumSizeHint>0 |
| 9 | `testNoCrashOnPaint` | 边界值：无数据绘制不崩溃 |

### 4.5 MapWidget 组件测试（8 项）

| # | 测试用例 | 测试方法 |
|---|---------|---------|
| 1 | `testConstructor` | 构造后尺寸≥600×400 |
| 2 | `testSetEmptyData` | 边界值：空数据不崩溃 |
| 3 | `testSetSinglePoint` | 等价类：1个地点 |
| 4 | `testSetMultiplePoints` | 等价类：3地点+订单+车辆+路径 |
| 5 | `testPlayPauseReset` | 语句覆盖：控制操作不崩溃 |
| 6 | `testSetSpeed` | 等价类：速度2x/10x |
| 7 | `testClear` | 语句覆盖：setData后clear |
| 8 | `testDefaultDimensions` | 构造后width/height≥0,minimum≥600/400 |

---

## 五、测试方法覆盖矩阵

### 等价类划分

| 模块 | 有效等价类 | 无效等价类 |
|------|-----------|-----------|
| 订单创建 | 正常时间窗、正 revenue、有效 locationId | timeEnd<timeStart、负 revenue、不存在 locationId |
| 订单状态 | UNASSIGNED/ASSIGNED/EXECUTING/DONE/CANCELLED | "INVALID"/空/null |
| 车辆创建 | 正常容量、速度>0、有效 locationId | 负容量、负速度 |
| 车辆状态 | IDLE/ON_DUTY/OFFLINE | "BROKEN" |
| 地点坐标 | lng[-180,180], lat[-90,90] | 超范围 |
| 地址解析 | 有效中国地址≥3字符 | 空/过短 |
| 调度 | 有车有单 | 无车/无单 |

### 边界值分析

| 边界条件 | 值 | 测试验证 |
|---------|-----|---------|
| 订单数 | 0, 1, N | empty/单/批量 |
| 车辆数 | 0, 1, N | 无车→unassigned |
| 容量等式 | weight=maxWeight | 刚好可行 |
| 时间窗 | aSec=bSec (即时) | 可行判定 |
| idle 时间 | 0（车辆在取货点） | 最小费用=0 |
| 收益 | 0, 负数 | 算法接受 |
| demand | 0 | solve(0) 空图 |
| 图规模 | 10车×10单 | 100+弧压力测试 |
| 下界 | lb>totalSupply | infeasible |

### 白盒覆盖

| 覆盖标准 | 测试数 | 关键覆盖点 |
|---------|--------|-----------|
| 语句覆盖 | 26+ | ArcBuilder#build 全部7类弧构建、OrderDao CRUD、ScheduleService 两阶段 |
| 分支覆盖 | 15+ | 容量约束 true/false、时间窗 true/false、shift 限制、下界可行性 |
| 路径覆盖 | 8+ | SSP→Dijkstra→增广→extractSolution、两级 arc cost 选择、级联删除 |
| 条件覆盖 | 5+ | ArcBuilder 多条件 feasibility 判断（时间/容量/shift 三条件 AND） |

### 黑盒覆盖率

| API 模块 | 端点总数 | 测试项数 | 覆盖率 |
|---------|---------|---------|--------|
| /api/orders | 8 | 17 | 100% |
| /api/vehicles | 10 | 9 | 90% |
| /api/locations | 6 | 6 | 100% |
| /api/schedule | 6 | 9 | 100% |
| /api/config | 2 | 4 | 100% |
| **合计** | **27** | **45** | **~96%** |

---

## 六、运行命令

```bash
# ========== 后端 ==========
cd server

# 运行全部测试（111项）
./mvnw test

# 仅运行算法测试
./mvnw test -Dtest="MinCostFlow*,ArcBuilder*"

# 仅运行 DAO 测试
./mvnw test -Dtest="*DaoTest"

# 仅运行 API 黑盒测试
./mvnw test -Dtest="RestApiBlackBoxTest"

# 运行端到端测试
./mvnw test -Dtest="EndToEndTest"

# ========== 前端 ==========
cd qtclient

# 构建测试
mkdir -p build-test && cd build-test
cmake ../tests -DCMAKE_PREFIX_PATH=~/Qt/6.8.0/gcc_64
cmake --build . -j

# 运行全部测试（5套）
ctest

# 运行单个测试
./test_models
./test_theme
./test_gantt
./test_mapwidget
./test_apiclient
```

---

## 七、遗留问题与建议

| 类别 | 问题 | 建议 |
|------|------|------|
| 前端 ApiClient | 无 mock HTTP 层，API 测试为冒烟测试 | 引入 Qt 网络 mock 框架或 fake 服务器 |
| 地图 API | geocode 测试依赖外部 Tencent Maps API | 添加 stub TencentGeocoderClient 用于 CI |
| 并发测试 | 缺少多线程/并发安全测试 | 添加 HikariCP 连接池并发测试 |
| 性能测试 | 大网络求解时间未测量 | 添加 100 单 benchmark 测试 |
| WASM 前端 | WASM 编译目标未纳入测试 | CI 中增加 WASM 交叉编译检查 |
| CSS 样式 | Theme 测试仅验证文本存在，未验证渲染效果 | 与 Qt UI 自动化 (Squish) 集成 |

---

> **结论：** 物流调度系统已完成全面测试，包括静态分析（Checkstyle + clang-tidy）、白盒测试（算法/DAO/Service/组件）、黑盒测试（27 REST 端点）和端到端集成测试，总计 171 项测试全部通过。测试方法覆盖等价类划分、边界值分析、语句覆盖、分支覆盖、路径覆盖、错误推测等，满足软件可用性与易用性验证要求。
