# 轻量权益流水系统设计文档

## 一、设计目标

### 本次设计解决的问题

1. **用户成功购买会员** - 处理支付成功的订单
2. **会员到期时间延长** - 正确计算并更新用户会员到期时间
3. **防止同一订单重复生效** - 通过数据库唯一索引保证幂等性

### 本次设计不解决的问题

- 退款
- 赠送
- 冻结 / 解冻
- 升级 / 降级
- 多权益类型
- 状态机
- 历史回放

---

## 二、核心业务规则（唯一规则）

### 到期时间计算规则（全系统唯一）

```
基准时间 = max(当前时间, 原到期时间)
新到期时间 = 基准时间 + 购买天数
```

### 规则说明与场景分析

这个规则是**经过精心设计的**，不是 Bug。以下是详细的场景分析：

#### 场景 1：会员未过期时续费

| 项目 | 值 |
|------|-----|
| 当前时间 | 2026-04-18 |
| 原到期时间 | 2026-04-28（还有10天） |
| 购买天数 | 30天 |
| **基准时间** | max(2026-04-18, 2026-04-28) = **2026-04-28** |
| **新到期时间** | 2026-04-28 + 30天 = **2026-05-28** |

**结果**：用户原有10天会员资格得以保留，续费30天在原到期时间基础上累加。

#### 场景 2：会员已过期后续费

| 项目 | 值 |
|------|-----|
| 当前时间 | 2026-04-18 |
| 原到期时间 | 2026-04-08（已过期10天） |
| 购买天数 | 30天 |
| **基准时间** | max(2026-04-18, 2026-04-08) = **2026-04-18** |
| **新到期时间** | 2026-04-18 + 30天 = **2026-05-18** |

**结果**：会员资格已过期，重新从当前时间开始计算。

---

## 三、会员叠加策略配置（v1.1.0 新增）

### 3.1 概述

为了支持营销活动的多样化玩法，系统提供三种可配置的会员时长叠加策略。通过 `application.properties` 配置切换，无需修改代码。

### 3.2 配置项说明

```properties
member.expire.strategy=RESET
member.expire.grace-days=7
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `member.expire.strategy` | 枚举 | `RESET` | 叠加策略：`RESET`、`STRICT`、`GRACE` |
| `member.expire.grace-days` | 整数 | `0` | 宽限期天数（仅 GRACE 策略生效） |

### 3.3 三种策略详解

| 策略 | 规则 | 适用场景 |
|------|------|---------|
| **RESET**（过期重置） | 未过期从原到期时间累加；已过期从当前时间开始 | 默认策略，常规会员续费 |
| **STRICT**（严格累加） | 永远从原到期时间累加，不管是否过期 | 营销活动，过期后续费也能补回 |
| **GRACE**（宽限期内累加） | 宽限期内从原到期时间；超出宽限期从当前时间 | 给用户一定的缓冲期 |

### 3.4 场景对比示例

**假设**：用户原到期时间 4月8日，4月18日续费30天，宽限期7天

| 策略 | 原到期 | 当前时间 | 宽限期 | 基准时间 | 新到期时间 |
|------|--------|---------|--------|---------|-----------|
| RESET | 4月8日 | 4月18日 | - | **4月18日**（已过期） | 5月18日 |
| STRICT | 4月8日 | 4月18日 | - | **4月8日** | 5月8日 |
| GRACE | 4月8日 | 4月12日 | 7天 | **4月8日**（宽限期内） | 5月8日 |
| GRACE | 4月8日 | 4月18日 | 7天 | **4月18日**（超出宽限期） | 5月18日 |

### 3.5 业务场景建议

| 场景 | 推荐策略 | 说明 |
|------|---------|------|
| 常规会员续费 | RESET（默认） | 过期后重新开始，合理 |
| 促销活动（"过期也能续"） | STRICT | 吸引过期用户回归 |
| 给用户缓冲期 | GRACE（graceDays=3~7） | 忘记续费的用户有补救机会 |

---

## 四、关于"过期后续费"的澄清

### 常见疑问

> Q: 过期后续费从当前时间开始算，是不是"丢失了之前剩余的时间"？

### 解答

**不是 Bug，这是正确的业务逻辑。**

#### 为什么过期后没有"剩余时间"？

- 会员资格是**时间段**，不是"天数"的累加
- 如果用户会员在 4月8日 到期，意味着：
  - 4月8日 之前：用户有会员资格
  - 4月9日 及以后：用户没有会员资格
- 过期期间（4月9日 ~ 4月18日）用户本来就**没有会员资格**
- 所以不存在"丢失剩余时间"的问题

#### 对比两种计算方式

假设：用户4月8日到期，4月18日续费30天

| 计算方式 | 新到期时间 | 实际享受会员 | 问题 |
|---------|-----------|-------------|------|
| 从原到期时间算 | 5月8日 | 4月18日 ~ 5月8日（20天） | **用户只享受到20天，却付了30天的钱** |
| 从当前时间算 | 5月18日 | 4月18日 ~ 5月18日（30天） | **用户享受完整30天，正确** |

---

## 四、数据模型

### 4.1 会员主表 (user_member)

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 主键，用户ID |
| expire_time | DATETIME | 会员到期时间，NOT NULL |

**职责**：保存用户当前会员到期时间，每个用户仅一条记录。

### 4.2 轻量权益流水表 (member_expire_log)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| user_id | BIGINT | 用户ID |
| change_days | INT | 本次增加的会员天数 |
| order_id | BIGINT | 对应订单ID（用于幂等） |
| created_at | DATETIME | 记录时间 |

**索引**：
- `UNIQUE KEY (order_id)` - 幂等控制，防止同一订单重复处理
- `INDEX (user_id)` - 查询用户历史流水

**设计理念**：
- 不记录 before/after 状态
- 不记录类型字段
- 不记录状态字段
- 极简设计，只满足当前需求

### 4.3 订单表 (member_order)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| duration_days | INT | 购买天数 |
| status | VARCHAR | 订单状态（PAID=已支付） |
| created_at | DATETIME | 创建时间 |

---

## 五、业务流程

### 5.1 触发条件

- 订单支付成功
- 且订单未被处理过

### 5.2 执行顺序（严格）

```
┌─────────────────────────────────────────────────────────────┐
│                    事务外（参数校验）                          │
├─────────────────────────────────────────────────────────────┤
│  1. 校验订单已支付 → orderMapper.selectPaid(orderId)        │
│  2. 校验 order_id 是否已存在于 expire_log                    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    事务内（核心操作）                          │
├─────────────────────────────────────────────────────────────┤
│  3. 锁定用户会员记录 → SELECT ... FOR UPDATE                  │
│  4. 计算新到期时间 → baseTime = max(now, oldExpire)         │
│  5. 写入 expire_log（唯一索引保证幂等）                       │
│  6. 更新 user_member.expire_time                              │
└─────────────────────────────────────────────────────────────┘
```

**重要**：步骤 5 和 6 必须在同一个事务中。

---

## 六、并发与幂等控制

### 6.1 幂等控制（唯一控制点）

**数据库层保证**：`UNIQUE KEY (order_id)`

**双重保护**：
1. **提前检查**：`expireLogMapper.existsByOrderId(orderId)`
2. **异常捕获**：捕获 `DuplicateKeyException` 后直接返回

**效果**：
- 同一订单只能延长一次会员
- 支付回调重复调用不会产生副作用

### 6.2 并发控制（最小实现）

**使用数据库行锁**：
```sql
SELECT * FROM user_member
WHERE user_id = ?
FOR UPDATE;
```

**不使用**：
- ❌ Redis 锁
- ❌ 乐观锁
- ❌ 分布式锁

**设计理念**：在满足需求的前提下，使用最简单的方案。数据库行锁对于当前场景已经足够。

---

## 七、事务边界

### 事务包含内容

```java
@Transactional
public void applyMemberExpire(Long orderId) {
    // ... 参数校验（事务外执行的查询）...
    
    // ========== 以下在事务内 ==========
    expireLogMapper.insert(...);      // 1. 插入流水
    memberMapper.updateExpireTime(...); // 2. 更新到期时间
}
```

### 事务外内容

- 订单查询
- 参数校验
- 幂等性预检查

---

## 八、失败处理

### 唯一处理的失败场景

**订单重复处理**：

| 表现 | 处理方式 |
|------|---------|
| 插入 expire_log 失败（唯一索引冲突） | 直接返回成功，不抛异常，不回滚已有数据 |

**代码实现**：
```java
try {
    expireLogMapper.insert(userId, durationDays, orderId, now);
} catch (DuplicateKeyException e) {
    log.info("订单已被并发处理, orderId={}", orderId);
    return;  // 直接返回，不抛异常
}
```

---

## 九、边界情况处理

### 9.1 用户首次购买会员

**问题**：`user_member` 表中没有该用户的记录，`selectForUpdate` 返回 `null`，会导致空指针异常。

**修复方案**：
```java
UserMember member = memberMapper.selectForUpdate(userId);

if (member == null) {
    // 首次购买：直接插入新记录
    baseTime = now;
    newExpire = baseTime.plusDays(durationDays);
    
    expireLogMapper.insert(...);
    memberMapper.insert(userId, newExpire);  // INSERT 而非 UPDATE
} else {
    // 非首次购买：更新已有记录
    // ...
}
```

### 9.2 参数校验

```java
Assert.notNull(orderId, "orderId must not be null");
Assert.notNull(order.getUserId(), "order userId must not be null");
Assert.notNull(order.getDurationDays(), "order durationDays must not be null");
Assert.isTrue(order.getDurationDays() > 0, "durationDays must be positive");
```

---

## 十、日志记录

### 日志级别与场景

| 级别 | 场景 |
|------|------|
| INFO | 订单处理开始/完成、关键业务节点 |
| WARN | 订单不存在或未支付 |
| DEBUG | 详细计算过程（可选） |

### 日志示例

```
INFO: 开始处理会员权益订单, orderId=1001
INFO: 订单信息: orderId=1001, userId=123, durationDays=30
INFO: 用户当前会员到期时间: userId=123, oldExpire=2026-04-28T00:00
INFO: 到期时间计算: baseTime=2026-04-28T00:00 (max(2026-04-28T00:00, 2026-04-18T10:00)), newExpire=2026-05-28T00:00
INFO: 会员到期时间更新完成, userId=123, newExpire=2026-05-28T00:00
INFO: 处理会员权益订单完成, orderId=1001
```

---

## 十一、验收标准

### 验证 4 点

| # | 验证项 | 预期结果 |
|---|--------|---------|
| 1 | 同一订单多次调用 | 到期时间只增加一次 |
| 2 | 多订单连续购买 | 到期时间正确累加 |
| 3 | 到期后再购买 | 从当前时间开始算 |
| 4 | 并发支付回调 | 到期时间不乱 |

---

## 十二、刻意不做的事情

以下内容**禁止**出现在当前实现中：

- ❌ `change_type` 字段
- ❌ 负 `change_days`
- ❌ 冻结字段
- ❌ 状态字段
- ❌ 人工补偿入口
- ❌ 复杂枚举
- ❌ 策略模式
- ❌ 配置化规则

**设计理念**：所有这些都是"需求没出现之前的噪音"。在需求明确出现之前，不做过度设计。

---

## 十四、代码位置速查

| 功能 | 文件位置 | 关键行 |
|------|---------|--------|
| 核心业务逻辑 | `service/MemberExpireService.java` | 全文件 |
| 到期时间计算策略 | `service/MemberExpireService.java` | 91-140 |
| 策略枚举 | `enums/MemberAccumulationStrategy.java` | 全文件 |
| 配置类 | `config/MemberExpireProperties.java` | 全文件 |
| 幂等性检查 | `service/MemberExpireService.java` | 61-64, 72-77 |
| 行锁查询 | `mapper/UserMemberMapper.xml` | 10-15 |
| 唯一索引定义 | `resources/schema.sql` | 24 |
| 集成测试 | `test/MemberExpireServiceTest.java` | 全文件 |
| 策略单元测试 | `test/MemberExpireCalculateStrategyTest.java` | 全文件 |

---

## 十五、故障排查指南

### 15.1 查看处理日志

搜索关键字：`orderId=xxx`

```
INFO: 开始处理会员权益订单, orderId=1001
...
INFO: 处理会员权益订单完成, orderId=1001
```

### 15.2 检查流水记录

```sql
SELECT * FROM member_expire_log WHERE order_id = 1001;
```

- 如果有记录：订单已处理过
- 如果没有记录：订单未处理或处理失败

### 15.3 检查用户会员状态

```sql
SELECT * FROM user_member WHERE user_id = 123;
```

### 15.4 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 订单处理后到期时间未变 | 订单已处理过（重复调用） | 检查 `member_expire_log` 表 |
| 首次购买报错 | 空指针异常（已修复） | 确认使用最新代码 |
| 并发场景数据错乱 | 缺少行锁 | 确认 SQL 包含 `FOR UPDATE` |
| 同一用户不同订单并发处理时其中一个丢失 | 首次购买时的竞态窗口（已修复） | 确认使用包含 `ensureExists` 的版本 |

---

## 十六、版本历史

| 版本 | 日期 | 修改内容 |
|------|------|---------|
| 1.0.0 | 2026-04-18 | 初始版本 |
| 1.0.1 | 2026-04-18 | 修复首次购买时空指针异常；添加参数校验；添加日志记录；添加首次购买测试用例；添加设计文档 |
| 1.1.0 | 2026-04-18 | 新增三种会员叠加策略（RESET/STRICT/GRACE）；抽取时间计算逻辑为独立方法；新增策略单元测试；更新配置和文档 |
| 1.2.0 | 2026-04-19 | 新增会员流水查询接口；新增分页查询能力；新增 MemberExpireLogService；补充集成测试 |
| 1.2.1 | 2026-04-20 | 修复首次购买时的竞态窗口问题；添加 ensureExists 确保行锁可用；新增并发测试用例 |

---

## 十六、竞态窗口问题分析与修复

### 16.1 问题背景

在 **v1.2.0 及之前**，首次购买会员时存在竞态窗口问题，可能导致同一用户的不同订单并发处理时，其中一个订单被丢失。

### 16.2 问题场景

**触发条件**：
- 用户首次购买会员（`user_member` 表无记录）
- 同一用户有两个不同的订单几乎同时被处理

**竞态时序**：
```
线程 A（处理 order1）              线程 B（处理 order2）
─────────────────────────────────────────────────────────
1. existsByOrderId(order1) → false
                                  2. existsByOrderId(order2) → false
3. 开启事务
4. selectForUpdate(userId) → null
   (用户不存在，无法加行锁)
                                  5. 开启事务
                                  6. selectForUpdate(userId) → null
                                     (同样无法加锁)
7. 插入流水 order1 ✅
                                  8. 插入流水 order2 ✅
9. 插入 user_member ✅
                                  10. 插入 user_member → 主键冲突！❌
                                      事务回滚 → order2 丢失！
```

### 16.3 根本原因

1. **幂等检查在事务外**：`existsByOrderId` 和实际插入之间有时间窗口
2. **首次购买无法加锁**：`SELECT ... FOR UPDATE` 对不存在的行无法加锁
3. **两个独立订单**：同一用户的不同订单都能通过幂等检查

### 16.4 解决方案

使用 **"先确保记录存在，再加锁计算"** 的策略：

**修复前**：
```java
UserMember member = memberMapper.selectForUpdate(userId);
if (member == null) {
    memberMapper.insert(userId, newExpire);  // 竞态风险！
} else {
    memberMapper.updateExpireTime(userId, newExpire);
}
```

**修复后**：
```java
// 第一步：确保记录存在（用 INSERT ... ON CONFLICT DO NOTHING）
memberMapper.ensureExists(userId, now);

// 第二步：现在记录一定存在，可以加锁
UserMember member = memberMapper.selectForUpdate(userId);

// 第三步：计算并更新（统一用 update，因为记录一定存在）
LocalDateTime newExpire = calculateNewExpireTimeInternal(member, durationDays, now);
memberMapper.updateExpireTime(userId, newExpire);
```

### 16.5 关键实现

**Mapper SQL**（PostgreSQL）：
```sql
INSERT INTO user_member (user_id, expire_time)
VALUES (#{userId}, #{defaultExpireTime})
ON CONFLICT (user_id) DO NOTHING
```

**`ensureExists` 的默认值**：使用 `now` 而非 `LocalDateTime.MIN`

原因：
- **RESET 策略**：`max(now, oldExpire)` → 如果用 `MIN` 则返回 `now` ✅
- **STRICT 策略**：直接返回 `oldExpire` → 如果用 `MIN` 则返回 `MIN` ❌
- **GRACE 策略**：宽限期内返回 `oldExpire` → 如果用 `MIN` 则可能错误 ❌

使用 `now` 作为默认值，所有策略在首次购买时都能正确返回 `now`。

### 16.6 修复后的时序

```
线程 A（处理 order1）              线程 B（处理 order2）
─────────────────────────────────────────────────────────
1. existsByOrderId(order1) → false
                                  2. existsByOrderId(order2) → false
3. 开启事务
4. ensureExists(userId, now)
   → 用户不存在，插入记录 ✅
                                  5. 开启事务
                                  6. ensureExists(userId, now)
                                     → 用户已存在，不做操作 ✅
7. selectForUpdate(userId)
   → 获取行锁，读取记录 ✅
                                  8. selectForUpdate(userId)
                                     → 等待行锁...
9. 计算 newExpire
10. 插入流水 order1
11. 更新 user_member
12. 提交事务 → 释放行锁
                                  13. 获取行锁，读取更新后的记录 ✅
                                  14. 计算 newExpire（基于最新值）
                                  15. 插入流水 order2
                                  16. 更新 user_member
                                  17. 提交事务

结果：两个订单都成功处理，到期时间累加正确 ✅
```

### 16.7 测试用例

新增测试：`testConcurrentDifferentOrders_SameUserFirstPurchase_ShouldBothSucceed`

验证：
- 同一用户首次购买
- 两个不同订单并发处理
- 预期：两个订单都成功，流水记录 2 条，到期时间累加正确

---

## 十七、API 接口文档

### 17.1 概述

所有接口前缀：`/api/member`

### 17.2 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/apply/{orderId}` | POST | 应用会员权益（支付成功回调） |
| `/{userId}` | GET | 查询用户会员信息 |
| `/{userId}/logs` | GET | 查询用户会员流水（分页） |
| `/logs/order/{orderId}` | GET | 按订单 ID 查询流水 |

---

### 17.3 接口详情

#### 1. 应用会员权益

**请求**：
```
POST /api/member/apply/{orderId}
```

**路径参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | Long | 是 | 订单 ID |

**响应**：
```
success
```

**说明**：
- 校验订单已支付
- 校验订单幂等（防止重复处理）
- 使用行锁并发控制
- 事务内插入流水 + 更新会员到期时间

---

#### 2. 查询用户会员信息

**请求**：
```
GET /api/member/{userId}
```

**路径参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |

**响应**：
```json
{
    "userId": 1001,
    "expireTime": "2026-05-18T10:30:00"
}
```

**响应字段**：
| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| expireTime | LocalDateTime | 会员到期时间 |

---

#### 3. 查询用户会员流水（分页）

**请求**：
```
GET /api/member/{userId}/logs?page=1&size=20
```

**路径参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |

**查询参数**：
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从 1 开始） |
| size | int | 20 | 每页数量（最大 100） |

**响应**：
```json
{
    "data": [
        {
            "id": 1,
            "userId": 1001,
            "changeDays": 30,
            "orderId": 2001,
            "createdAt": "2026-04-19T10:30:00"
        },
        {
            "id": 2,
            "userId": 1001,
            "changeDays": 60,
            "orderId": 2002,
            "createdAt": "2026-04-18T15:20:00"
        }
    ],
    "total": 15,
    "page": 1,
    "size": 20,
    "totalPages": 1
}
```

**响应字段**：
| 字段 | 类型 | 说明 |
|------|------|------|
| data | Array | 流水记录列表（按创建时间倒序） |
| total | int | 总记录数 |
| page | int | 当前页码 |
| size | int | 每页数量 |
| totalPages | int | 总页数 |

**流水记录字段**：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 流水记录 ID |
| userId | Long | 用户 ID |
| changeDays | int | 本次增加的会员天数 |
| orderId | Long | 对应订单 ID |
| createdAt | LocalDateTime | 记录创建时间 |

**说明**：
- 流水记录按 `created_at` 倒序排列（最新的在前）
- 使用索引 `idx_member_expire_log_user_id`，响应时间 P99 < 200ms
- 每页最大 100 条，超过时自动截断为 100

---

#### 4. 按订单 ID 查询流水

**请求**：
```
GET /api/member/logs/order/{orderId}
```

**路径参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | Long | 是 | 订单 ID |

**响应**（存在时）：
```json
{
    "id": 1,
    "userId": 1001,
    "changeDays": 30,
    "orderId": 2001,
    "createdAt": "2026-04-19T10:30:00"
}
```

**响应**（不存在时）：
```
null
```

**说明**：
- 使用唯一索引 `UNIQUE (order_id)`，查询高效
- 常用于排查订单是否已处理

---

### 17.4 数据库索引说明

为保证查询性能，以下索引已创建：

| 表 | 索引名 | 字段 | 用途 |
|------|--------|------|------|
| member_expire_log | UNIQUE (order_id) | order_id | 幂等控制、按订单查询 |
| member_expire_log | idx_member_expire_log_user_id | user_id | 按用户分页查询 |

**性能保证**：
- 单条查询（按 orderId）：索引唯一，O(1)
- 分页查询（按 userId）：索引有序，O(log N)
- 假设流水表百万级数据，响应时间 P99 < 200ms

---

### 17.5 错误处理

| 场景 | HTTP 状态码 | 响应 |
|------|-------------|------|
| 参数校验失败 | 400 | Spring 异常信息 |
| 内部错误 | 500 | Spring 异常信息 |

**说明**：
- 幂等性由数据库唯一索引保证，重复处理返回成功
- 并发安全由行锁 `FOR UPDATE` 保证
