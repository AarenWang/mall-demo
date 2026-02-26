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
