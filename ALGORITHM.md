# 调度算法设计详解

## 算法总体：把"总闲置最短"建模为**最小路径覆盖 = 最小费用最大流**

核心在 `ArcBuilder.java:10-39`。调度问题被转成一张有向图：每个订单必须被"覆盖"一次，一辆车的服务链就是图中一条 S→…→T 的路径。求"总闲置时间最短" ≡ 求这张图的**最小费用最大流**，用 SSP(连续最短路)+Dijkstra+势函数精确求解。

---

## 一、闲置时间的定义（费用来源）

README 说"订单尾部是闲置起点，头部是闲置终点"。代码两处计算：

**订单接订单**（`ArcBuilder.java:134-137`）：
```
arriveSec = oi.eSec + travel(i.delivery → j.pickup)   // 干完 i 赶到 j 取货点的到达时刻
idle      = max(0, arriveSec − oj.aSec)                // 早到才有闲置(等待);晚到 idle=0(顺延)
```
其中 `eSec = a + travel(pickup→delivery) + service`（`ArcBuilder.java:74-77`, `OrderView.java:19`），即订单自身结束时刻。

**车辆接首单**（`ArcBuilder.java:108-112`）：
```
arriveSec = v.curAvailSec + travel(车当前位置 → j.pickup)
idle      = max(0, arriveSec − o.aSec)
```

这个 `idle` 就是每条弧的**费用 cost**。SSP 让总费用最小，即总闲置最短。

---

## 二、建图结构（`ArcBuilder.java:13-30`）

节点编号（V 辆车、n 单）：
```
0                = S（源）
1 .. V           = L_v  车辆出点
V+1 .. V+n       = R_j  订单入点
V+n+1 .. V+2n    = L_j  订单出点
V+2n+1           = T（汇）
```

六类边：

| 边 | 容量 | 费用 | 含义 |
|---|---|---|---|
| `S→L_v` | 1 | λ·base_v + 收益惩罚 | 车 v 启动一条链 (`:83`) |
| `S→L_j` | 1 | sinkPenaltyLj | j 作续接起点(阶段1=0,阶段2=M) (`:90`) |
| `S→R_j` | 1 | M | 兜底:j 无前驱→未分配 (`:97`) |
| `L_v→R_j` | 1 | idle(v,j) | 车 v 接 j 作首单 (`:120`) |
| `L_i→R_j` | 1 | idle(i,j) | i 后紧接 j (`:141`) |
| `R_j→T` | 1 | 0 | j 被覆盖 (`:148`) |

`demand = n`，每单位流覆盖一个订单。三种路径形态：
- 车链首：`S→L_v→R_j→T`（v 接 j）
- 续接：`S→L_j→R_k→T`（j→k，j 已由某车链覆盖）
- 兜底：`S→R_j→T`（j 未分配，付出巨大费用 M）

关键点：`L_x→R_y` 这条边被使用，语义就是"**x 是 y 的前驱**"。把整个链拆成一个个"前驱→后继"的匹配（二部图匹配），这正是**最小路径覆盖**的经典建模——n 个订单默认 n 条链（全兜底），每用一条 `L_x→R_y` 就把两条链合并成一条，从而减少车辆/减少闲置。

---

## 三、可行性剪枝（建图时就过滤）

不满足硬约束的弧根本不加入图，保证解一定合法：

- 订单自身可行：`e_i ≤ b_i`（`OrderView.java:48`）
- 首单（`ArcBuilder.java:116-118`）：`actualEnd ≤ b_j`（时间窗）、`actualStart/End` 落在车辆班次内、`weight/volume ≤` 车辆载重体积
- 续单（`:139`）：`actualEnd ≤ b_j`

`actualStart = max(o.aSec, arriveSec)`（`:111,136`）——允许车辆晚到顺延，但不能违反最晚送达。

---

## 四、两阶段求解——防"无车环"（核心 trick）

问题：阶段1 里 `S→L_j` 费用为 0，SSP 可能全走 `S→L_j→R_k→T`，绕过所有车辆形成"无车环"，所有订单互为前驱却没有一辆车真正启动。

解法（`ScheduleService.java:252-259`）：
```
阶段1: sinkPenaltyLj = 0，正常求解
       extractSolution 只从 S→L_v 拼接真实车链，无车环里的订单→unassigned
阶段2: 若阶段1全部 unassigned，用 sinkPenaltyLj = M 重解
       S→L_j 变昂贵 → SSP 被迫使用 S→L_v，强制车辆启动
```

---

## 五、最小费用流求解器（`MinCostFlow.java`）

**无下界（本问题主路径）** `solveSimple:63-115`：标准 SSP。
- 每轮用 **Dijkstra + 势函数**（reduced cost `rc = cost + h[v] − h[to]`，`:84`）跑最短路，避开负权、复杂度 O(F·E·logV)
- 势更新 `potential[v] += dist[v]`（`:94-96`）
- 每次沿最短路增广 1 单位（`aug=1`，因所有容量为1），累加 `pathCost`
- `dist[T]=∞` 即达最大流停止

**带下界（通用能力）** `solveWithLowerBounds:132-181`：经典两阶段超级源汇法。
- 下界边转化：容量 `ub−lb`，`supply[to]+=lb, supply[from]-=lb`（`:33-45`）
- 阶段1：加 SS/TT + 辅助边 `T→S(∞)`，跑 Edmonds-Karp 最大流判可行（`:152-154`）
- 阶段2：残余网络上跑 S→T SSP 到 demand（`:175-177`）

（本调度问题的弧都无下界，走的是 `solveSimple`；下界能力是求解器的通用扩展，有 `MinCostFlowTest` 覆盖。）

---

## 六、解的提取（`extractSolution:244-317`）

从残余图反推路线（正向边 `cap==0` 表示已用）：
1. 收集前驱 `predecessor[y]=x`（扫所有已用 `L_x→R_y`，`:257-267`）
2. 收集兜底集 `S→R_j` 已用（`:270-276`）
3. 从每个已用 `S→L_v` 出发拼链：`L_v→R_j` 找首单 j，再看 `S→L_j` 是否续接找 `L_j→R_k`…直到断链（`:281-302`），用 `visited` 防环
4. 未被任何车链覆盖的兜底订单 → `unassigned`（`:305-314`）

---

## 七、时间线重算与落库（`ScheduleService.java:287-322`）

拿到订单链后按车速真实推演每一段：`arrive → plannedStart=max(a,arrive) → idleBefore → end=start+travelPD+service`，累计 `totalIdle`，写入 `RouteLeg`，再 `persistSchedule` 落库为 route/route_item。

---

## 八、动态调度（rolling horizon，`scheduleDynamic:92-132`）

- **冻结集**：PLANNED/EXECUTING 路线上的订单锁定，不进新求解
- 车辆初始状态用其冻结路线**末单**的 `delivery_loc + planned_end` 修正（`:101-117, 431-445`）
- 只对 UNASSIGNED 订单 + 可用车辆(IDLE/ON_DUTY)重新建图求解

---

## 补充：距离服务（`DistanceService.java`）

三级：DB 缓存表 → 地图 API → 欧氏 haversine 兜底（30km/h 估速，`:57-60`），结果回写缓存，避免重复调用。

---

## 现状：收益均衡（A方案：高于平均惩罚）

`S→L_v` 弧费用接入收益均衡惩罚（`ArcBuilder.java:79-101`）：
```
avgEarned      = 全体车辆 earnedRevenue 的平均值
revenuePenalty = round(revenueUnit * max(0, earned_v − avgEarned))
revenuePenalty = min(revenuePenalty, penaltyM/2)          // 上限 clamp
cost(S→L_v)    = λ·base_v + revenuePenalty
```
以全体车辆的**平均**已获收益为基准：只惩罚"高于平均"的车，惩罚额 = 超出均值的差值，SSP 自然把新单优先分给收益≤平均的车 → 个体收益均衡。低于/等于平均者惩罚为 0；全部收益相等（含全 0）时退化为纯 `λ·base_v`。

**"唯一可行车必派单"**：惩罚上限 clamp 到 `penaltyM/2`，保证 `penalty + idle < 兜底费用 M`。因此覆盖某订单时，即使唯一可行的车收益高于平均，走 `S→L_v→R_j→T` 仍比放弃订单（兜底 `S→R_j→T`，费用 M）更便宜，最小费用流会自动派给它而非丢单。

`revenueUnit`（默认 100，`AppConfig.java:65`）是"元→等效闲置秒"的量纲换算系数，可调。



**每单默认独立成链（付 M），用二部图匹配把相邻两单拼起来省下 M 并累加它们之间的闲置作为费用，最小费用最大流就在"尽量少留兜底 + 拼接时闲置最小"之间求出全局精确最优解**，两阶段惩罚解决无车环退化，Dijkstra+势函数保证效率。
