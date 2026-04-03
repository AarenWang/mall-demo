# Mall Demo

用于演示商城（Mall）接入加密支付平台的最小闭环：

1. 商城下单页创建订单（法币币种 + 法币金额）
2. 商城后端签名调用支付网关 `POST /api/v1/payments`
3. 前端跳转收银台 `/pay/{paymentId}`
4. 支付系统通过 webhook 回调商城后端
5. 商城结果页轮询订单状态并展示终态

## 目录

- `backend/`：Spring Boot 3 + Java 17
- `frontend/`：React + TypeScript + Vite
- `scripts/`：本地一键启动/停止脚本

## 快速开始（单商户模式）

先复制配置示例：

```bash
cd mall-demo
cp .env.backend.example .env.backend
cp .env.frontend.example .env.frontend
```

然后按需加载环境变量后启动：

```bash
cd mall-demo
set -a
source .env.backend
source .env.frontend
set +a
./scripts/start-local.sh
```

## 多租户支持（多商户模式）

Mall-demo 现已支持多租户，可以同时模拟多个商户接入支付系统。

### 启动多个商户前端

```bash
# 商户 A 前端 (端口 5174)
cd mall-demo/frontend
pnpm install
cp .env.frontend.merchantA .env.frontend
pnpm dev --host --port 5174

# 商户 B 前端 (端口 5175，在另一个终端)
cd mall-demo/frontend
cp .env.frontend.merchantB .env.frontend
VITE_PORT=5175 pnpm dev --host
```

### 后端配置多商户

后端通过 `X-Merchant-Id` header 识别不同商户，自动使用对应的 API 凭证。

在 `backend/src/main/resources/application-local.yaml` 中配置：

```yaml
mall:
  demo:
    merchants:
      merchant_a:
        apiKey: "your_api_key_a"
        apiSecret: "your_api_secret_a"
        name: "Merchant A"
      merchant_b:
        apiKey: "your_api_key_b"
        apiSecret: "your_api_secret_b"
        name: "Merchant B"
```

### 工作流程

```
前端A (5174) --X-Merchant-Id: merchant_a--> 后端 API --> 使用商户A凭证调用支付网关
前端B (5175) --X-Merchant-Id: merchant_b--> 后端 API --> 使用商户B凭证调用支付网关
```

## 启动后端

```bash
cd mall-demo/backend
mvn spring-boot:run
```

默认端口：`18080`

环境变量（关键）：

- `MALL_DB_URL` / `MALL_DB_USERNAME` / `MALL_DB_PASSWORD`：商城 demo MySQL
- `PAYMENT_GATEWAY_BASE_URL`：支付网关地址（默认 `http://localhost:8080`）
- `PAYMENT_API_KEY` / `PAYMENT_API_SECRET`：商户 API Key/Secret（单商户模式）
- `PAYMENT_CHECKOUT_BASE_URL`：收银台站点地址（默认 `http://localhost:5173`）
- `MALL_WEBHOOK_SECRET`：商城 webhook 验签 secret
- `MALL_FRONTEND_RESULT_BASE_URL`：商城结果页地址（默认 `http://localhost:5174/result`）

## 启动前端

```bash
cd mall-demo/frontend
pnpm install
pnpm dev --host --port 5174
```

环境变量：

- `VITE_MALL_API_BASE`：商城后端地址（默认 `http://localhost:18080`）
- `VITE_MERCHANT_ID`：商户标识（默认 `merchant_a`）

## 已实现接口

- `POST /api/mall/orders`
- `GET /api/mall/orders/{orderId}`
- `GET /api/mall/orders/{orderId}/payment-status`
- `POST /api/mall/webhooks/payment`

## 商户接入说明

mall-demo 当前演示的是“商户后端创建支付单，再跳转 checkout”的标准接入方式。

### 1. 创建支付单

商户后端调用支付网关：

- 接口：`POST /api/v1/payments`
- 认证：HMAC 签名请求头 `X-Api-Key` / `X-Signature` / `X-Timestamp` / `X-Nonce`
- 幂等：建议传 `X-Idempotency-Key`

mall-demo 当前实际发送的关键字段：

- `orderId`
- `amount`
- `fiatCurrency`
- `cryptoOptions`
- `title`
- `description`
- `returnUrl`
- `cancelUrl`

示例请求体：

```json
{
  "orderId": "MALL-20260403-ABC12345",
  "amount": 99.90,
  "fiatCurrency": "USD",
  "title": "iPhone 15 Case",
  "description": "iPhone 15 Case x1 (MALL-20260403-ABC12345)",
  "returnUrl": "http://localhost:5174/result?orderId=MALL-20260403-ABC12345",
  "cancelUrl": "http://localhost:5174/result?orderId=MALL-20260403-ABC12345",
  "cryptoOptions": [
    {
      "chain": "ANVIL",
      "asset": "USDT"
    }
  ]
}
```

### 2. 新增字段的推荐语义

- `title`
  - 用于 checkout 主标题展示。
  - 建议传商品名、订单标题或用户最容易识别的支付主题。
- `description`
  - 用于 checkout 副标题或补充说明。
  - 建议传简短订单摘要，不要传完整订单明细。
- `returnUrl`
  - 支付成功后，checkout 返回商户页面的地址。
  - 建议落到商户自己的结果页，再由商户后端按 `orderId` 回查订单状态。
- `cancelUrl`
  - 用户主动取消、关闭支付或需要回退时，checkout 返回商户页面的地址。
  - 一期可以和 `returnUrl` 指向同一个结果页，由商户页面自行区分状态。

### 3. 当前 mall-demo 的实现方式

- `title = 商品名称`
- `description = 商品名称 + 数量 + 订单号`
- `returnUrl = MALL_FRONTEND_RESULT_BASE_URL + ?orderId=...`
- `cancelUrl = MALL_FRONTEND_RESULT_BASE_URL + ?orderId=...`

对应实现见：

- [MallOrderService.java](backend/src/main/java/com/cryptopay/malldemo/service/MallOrderService.java)
- [PaymentGatewayClient.java](backend/src/main/java/com/cryptopay/malldemo/service/PaymentGatewayClient.java)

### 4. checkout 回跳参数约定

checkout 回跳到商户前端后，建议商户优先依赖 `orderId` 查询自己的订单状态，而不是直接信任前端 query 参数中的支付状态。

mall-demo 结果页当前兼容以下参数：

- `orderId`
- `paymentId`
- `paymentNo`
- `status`
- `from=checkout`

其中：

- `paymentId` / `paymentNo` 主要用于结果页展示
- 最终订单状态仍以商户后端查询结果或 webhook 入库结果为准

### 5. webhook 是否需要配套修改

这次 checkout UI 改版不要求商户同步修改 webhook 协议。

mall-demo 当前仍通过：

- `POST /api/mall/webhooks/payment`

接收支付平台回调，并基于以下原则处理：

- 先验签 raw body
- 通过 `X-Webhook-Id` 做幂等
- 同时兼容 `payment_id/paymentId/payment_no/paymentNo`
- 同时兼容 `order_id/orderId`

所以：

- 创建支付接口建议尽快升级到 `title/description/returnUrl/cancelUrl`
- webhook 逻辑可以保持现状，不需要因为这次 UI 改版而强制修改

### 6. 关于 `callbackUrl`

`callbackUrl` 属于旧接入习惯，mall-demo 已不再用它驱动 checkout 回跳。

当前推荐方式是：

- 用 `returnUrl` 表达支付成功后的商户回跳地址
- 用 `cancelUrl` 表达支付取消或返回时的商户回跳地址
- webhook 继续作为服务端最终状态同步通道

这样可以避免商户再手工拼接 checkout URL 参数，也更贴近当前 checkout 的正式 contract。

## 当前实现说明

- 数据库存储：MySQL + Flyway（`user_order` / `pay_order` / `webhook_log`）。
- 多租户支持：
  - 前端通过 `VITE_MERCHANT_ID` 指定商户标识
  - 后端通过 `X-Merchant-Id` header 识别商户
  - 订单表包含 `merchant_id` 字段用于区分
- 已实现：
  - 网关 HMAC 签名调用（`X-Api-Key`/`X-Signature`/`X-Timestamp`/`X-Nonce`）
  - webhook raw body 验签
  - webhook 幂等（基于 `X-Webhook-Id`）
  - 状态映射与结果页轮询
- 目前测试：`HmacUtil` 单测 + `MallWebhookService` 单测。
