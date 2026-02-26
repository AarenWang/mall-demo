WebhookDeliveryService.java (支付系统后端):
  1. 修改 sendHttpRequest() 方法签名，新增 merchantId 参数
  2. 在发送 webhook 时添加 X-Merchant-Id header:
   .addHeader("X-Merchant-Id", merchantId != null ? String.valueOf(merchantId) : "")
  3. 更新两处调用：
    - deliverWebhook() - 初始发送
    - retryPendingWebhooks() - 重试发送

  MallWebhookService.java (mall-demo后端):
  1. 回退方案B的改动，移除 resolveMerchantId() 逻辑
  2. 添加注释说明 MerchantOriginFilter 会自动处理 X-Merchant-Id

  工作流程

  支付系统发送 Webhook
      ↓
  添加 X-Merchant-Id header (如: "3")
      ↓
  Mall-demo MerchantOriginFilter 拦截
      ↓
  提取 X-Merchant-Id → 设置 MallMerchantContext
      ↓
  MallWebhookService.process() 处理业务
      │
      ↓
  MerchantOriginFilter.afterCompletion() 清理上下文

  优势

  - 适用所有类型单据：支付、退款、提现等都可以正确识别商户
  - 自动上下文管理：MerchantOriginFilter 统一处理，业务代码无感知
  - 支持重试：webhook 重试时也会携带正确的 merchant_id

✻ Baked for 51s

❯ mall-demo的改动，需要如何配置和运行，再输出一次

⏺ Mall-Demo 多租户配置运行指南

  改动文件汇总

  前端:
  - frontend/src/api.ts - 添加 X-Merchant-Id header
  - .env.frontend.merchantA - 商户A环境变量
  - .env.frontend.merchantB - 商户B环境变量

  后端:
  - MallDemoProperties.java - 新增 merchants Map 支持多商户配置
  - MallMerchantContext.java - 新增商户上下文类
  - MerchantOriginFilter.java - 新增过滤器，从 X-Merchant-Id 提取商户信息
  - PaymentGatewayClient.java - 从上下文动态获取商户凭证
  - MallOrder.java / PayOrder.java - 新增 merchantId 字段
  - UserOrderRepository.java / PayOrderRepository.java - 支持 merchant_id 字段

  数据库:
  - V2__add_merchant_id.sql - 新增 merchant_id 字段

  支付系统:
  - WebhookDeliveryService.java - 发送 webhook 时携带 X-Merchant-Id header

  ---
  配置步骤

  1. 数据库迁移

  启动 mall-demo 后端会自动执行 Flyway 迁移：

  # 手动执行 SQL（可选）
  ALTER TABLE user_order ADD COLUMN merchant_id VARCHAR(64) DEFAULT 'merchant_a' NOT NULL;
  ALTER TABLE pay_order ADD COLUMN merchant_id VARCHAR(64) DEFAULT 'merchant_a' NOT NULL;

  2. 后端配置多商户凭证

  方式一：YAML 配置（推荐）

  创建 backend/src/main/resources/application-local.yaml：

  mall:
    demo:
      merchants:
        merchant_a:
          apiKey: "merchant_a_api_key"
          apiSecret: "merchant_a_api_secret"
          name: "Merchant A"
        merchant_b:
          apiKey: "merchant_b_api_key"
          apiSecret: "merchant_b_api_secret"
          name: "Merchant B"

  方式二：环境变量（兼容单商户）

  PAYMENT_API_KEY=your_api_key
  PAYMENT_API_SECRET=your_api_secret

  3. 获取商户 API 凭证

  从支付系统数据库查询：

  SELECT id, merchant_id, api_key, api_secret_enc
  FROM merchant_api_keys
  WHERE status = 'ACTIVE';

  或通过 Ops 界面查看商户 API Key。

  ---
  运行步骤

  启动后端

  cd mall-demo/backend
  mvn spring-boot:run

  启动商户A前端

  cd mall-demo/frontend
  cp .env.frontend.merchantA .env.frontend
  pnpm install
  pnpm dev --host --port 5174

  访问: http://localhost:5174

  启动商户B前端（新终端）

  cd mall-demo/frontend
  cp .env.frontend.merchantB .env.frontend
  pnpm dev --host --port 5175

  访问: http://localhost:5175

  ---
  工作流程

  ┌─────────────────┐     X-Merchant-Id: merchant_a     ┌─────────────────┐
  │  前端A (5174)   │ ──────────────────────────────────▶ │                 │
  │ VITE_MERCHANT_ID │                                    │  Mall-Demo      │
  │   = merchant_a  │                                    │  Backend        │
  └─────────────────┘                                    │  (18080)        │
                                                         │                 │
  ┌─────────────────┐     X-Merchant-Id: merchant_b     │  ┌───────────┐  │
  │  前端B (5175)   │ ──────────────────────────────────▶ │  │  Filter   │  │
  │ VITE_MERCHANT_ID │                                    │  │ Extracts  │  │
  │   = merchant_b  │                                    │  │ Context   │  │
  └─────────────────┘                                    │  └───────────┘  │
                                                         │         │       │
                                                         │  ┌──────▼─────┐│
                                                         │  │GatewayClient││
                                                         │  │Uses Creds  ││
                                                         │  └──────┬─────┘│
                                                         │         │       │
                                                         └─────────┼───────┘
                                                                   │
                                                                   ▼
                                                      ┌─────────────────────────┐
                                                      │  Payment Gateway (8080)│
                                                      │  X-Api-Key: merchant_xxx│
                      ┌─────────────────────────────────┤  X-Signature: ...     │
                      │ Webhook + X-Merchant-Id          └─────────────────────────┘
                      └─────────────────────────────────────▶

  ---
  验证测试

  1. 商户A下单: 前端A创建订单 → 后端使用商户A凭证调用支付网关
  2. 商户B下单: 前端B创建订单 → 后端使用商户B凭证调用支付网关
  3. Webhook回调: 支付系统发送 webhook 携带 X-Merchant-Id → Mall-demo 正确路由到对应商户
