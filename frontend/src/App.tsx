import { ReactNode, useEffect, useMemo, useState } from 'react'
import {
  createOrder,
  getOrder,
  getOrderPaymentStatus,
  listOrders,
  OrderStatusResponse
} from './api'

const terminalStatuses = new Set(['PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED', 'FAILED'])

const statusText: Record<string, string> = {
  PENDING: '待支付',
  PROCESSING: '处理中',
  PARTIALLY_PAID: '部分支付，需补足',
  PAID: '支付成功',
  EXPIRED: '已过期',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
  FAILED: '支付失败'
}

function formatMoney(value: string | number, currency: string) {
  const amount = Number(value)
  if (Number.isNaN(amount)) {
    return `${value} ${currency}`
  }
  return `${amount.toFixed(2)} ${currency}`
}

interface ProductItem {
  id: string
  name: string
  image: string
  fiatAmount: string
  fiatCurrency: string
  quantity: number
}

const productCatalog: ProductItem[] = [
  {
    id: 'chanel-25-handbag',
    name: 'Chanel 25 Leather Handbag',
    image: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '13726.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'tesla-model-s',
    name: 'Tesla Model S',
    image: 'https://g.autoimg.cn/@img/car3/cardfs/product/g27/M00/76/24/400x300_q80_c42_autohomecar__ChtlxmUMX_CAfkiKABo7_YHchtU616.jpg.webp?format=webp',
    fiatAmount: '84499.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'iphone-17',
    name: 'iPhone 17',
    image: 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '999.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'ipad-pro',
    name: 'iPad Pro',
    image: 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '1299.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'ledger-hw-wallet',
    name: 'Ledger 硬件钱包',
    image: 'https://images.unsplash.com/photo-1639762681485-074b7f938ba0?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '149.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'onekey-hw-wallet',
    name: 'OneKey 硬件钱包',
    image: 'https://images.unsplash.com/photo-1518773553398-650c184e0bb3?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '119.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'trezor-hw-wallet',
    name: 'Trezor 硬件钱包',
    image: 'https://images.unsplash.com/photo-1621761191319-c6fb62004040?auto=format&fit=crop&w=1200&q=80',
    fiatAmount: '99.00',
    fiatCurrency: 'USD',
    quantity: 1
  },
  {
    id: 'fujifilm-gfx100-ii',
    name: 'Fujifilm GFX100 II 中画幅无反相机',
    image: 'https://i.ebayimg.com/images/g/LAgAAeSw44BoTZrC/s-l1600.webp',
    fiatAmount: '11000.00',
    fiatCurrency: 'USD',
    quantity: 1
  }
]

interface PaymentMethodItem {
  id: string
  name: string
  desc: string
}

const paymentMethods: PaymentMethodItem[] = [
  {
    id: 'crypto-stablecoin',
    name: 'CryptoPayment StableCoin',
    desc: 'USDT / USDC 等稳定币支付'
  }
]

interface ShellProps {
  title: string
  subtitle?: string
  activeNav: 'create' | 'orders'
  children: ReactNode
}

function Shell({ title, subtitle, activeNav, children }: ShellProps) {
  return (
    <div className="page">
      <div className="card">
        <div className="top-nav">
          <button
            type="button"
            className={`nav-link ${activeNav === 'create' ? 'active' : ''}`}
            onClick={() => (window.location.href = '/')}
          >
            下单
          </button>
          <button
            type="button"
            className={`nav-link ${activeNav === 'orders' ? 'active' : ''}`}
            onClick={() => (window.location.href = '/orders')}
          >
            我的订单
          </button>
        </div>

        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
        {children}
      </div>
    </div>
  )
}

function CreateOrderPage() {
  const [selectedProductId, setSelectedProductId] = useState(productCatalog[0].id)
  const [creating, setCreating] = useState(false)
  const [createdOrder, setCreatedOrder] = useState<{ orderId: string; paymentId: string; checkoutUrl: string } | null>(null)
  // Store selected product info to display after order creation
  const [orderProductInfo, setOrderProductInfo] = useState<ProductItem | null>(null)
  const [error, setError] = useState<string | null>(null)

  const selectedProduct = useMemo(
    () => productCatalog.find((item) => item.id === selectedProductId) ?? productCatalog[0],
    [selectedProductId]
  )

  const createMallOrder = async () => {
    setCreating(true)
    setError(null)
    try {
      const payload = {
        fiatCurrency: selectedProduct.fiatCurrency,
        fiatAmount: selectedProduct.fiatAmount,
        productName: selectedProduct.name,
        productImage: selectedProduct.image,
        quantity: selectedProduct.quantity
      }
      const created = await createOrder(payload)
      setCreatedOrder(created)
      setOrderProductInfo(selectedProduct)
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建订单失败')
    } finally {
      setCreating(false)
    }
  }

  const resetOrder = () => {
    setCreatedOrder(null)
    setOrderProductInfo(null)
    setError(null)
  }

  const toPayPage = () => {
    if (!createdOrder) {
      setError('请先创建订单')
      return
    }
    window.location.href = `/pay?orderId=${encodeURIComponent(createdOrder.orderId)}`
  }

  return (
    <Shell title="Mall Demo" subtitle="步骤A：选择商品，下单（创建订单）" activeNav="create">
      {createdOrder && orderProductInfo ? (
        <div className="order-created">
          <h3>订单创建成功</h3>
          <div className="product-row">
            <img src={orderProductInfo.image} alt={orderProductInfo.name} />
            <div>
              <strong>{orderProductInfo.name}</strong>
              <div>
                金额: {formatMoney(orderProductInfo.fiatAmount, orderProductInfo.fiatCurrency)} | 数量:{' '}
                {orderProductInfo.quantity}
              </div>
            </div>
          </div>
          <div>订单号: {createdOrder.orderId}</div>
          <div>支付单号: {createdOrder.paymentId}</div>
          <div>商品名称: {orderProductInfo.name}</div>
          <div>订单金额: {formatMoney(orderProductInfo.fiatAmount, orderProductInfo.fiatCurrency)}</div>
          <button type="button" className="secondary-button" onClick={toPayPage}>
            下一步：选择支付方式
          </button>
          <button type="button" className="secondary-button" onClick={resetOrder}>
            重新下单
          </button>
        </div>
      ) : (
        <>
          <div className="product-grid">
            {productCatalog.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`product-option ${selectedProductId === item.id ? 'selected' : ''}`}
                onClick={() => {
                  setSelectedProductId(item.id)
                }}
              >
                <img src={item.image} alt={item.name} />
                <div className="product-meta">
                  <strong>{item.name}</strong>
                  <span>{formatMoney(item.fiatAmount, item.fiatCurrency)}</span>
                </div>
              </button>
            ))}
          </div>

          <div className="product-row">
            <img src={selectedProduct.image} alt={selectedProduct.name} />
            <div>
              <strong>{selectedProduct.name}</strong>
              <div>
                金额: {formatMoney(selectedProduct.fiatAmount, selectedProduct.fiatCurrency)} | 数量:{' '}
                {selectedProduct.quantity}
              </div>
            </div>
          </div>

          <button type="button" onClick={() => void createMallOrder()} disabled={creating}>
            {creating ? '创建订单中...' : '创建订单'}
          </button>
        </>
      )}

      {error ? <div className="error">{error}</div> : null}
    </Shell>
  )
}

function PayPage() {
  const query = useMemo(() => new URLSearchParams(window.location.search), [])
  const orderId = query.get('orderId')
  const [selectedMethodId, setSelectedMethodId] = useState(paymentMethods[0].id)
  const [order, setOrder] = useState<OrderStatusResponse | null>(null)
  const [loading, setLoading] = useState(Boolean(orderId))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!orderId) {
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    getOrder(orderId)
      .then((detail) => {
        if (!active) {
          return
        }
        setOrder(detail)
      })
      .catch((err) => {
        if (!active) {
          return
        }
        setError(err instanceof Error ? err.message : '查询订单失败')
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [orderId])

  const goPay = () => {
    if (!order) {
      setError('订单未就绪，请稍后重试')
      return
    }
    if (selectedMethodId !== 'crypto-stablecoin') {
      setError('当前仅支持 CryptoPayment StableCoin')
      return
    }
    window.location.href = order.checkoutUrl
  }

  if (!orderId) {
    return (
      <Shell title="选择支付方式" subtitle="步骤B：去支付" activeNav="create">
        <div className="error">缺少 orderId，请先创建订单</div>
        <button type="button" onClick={() => (window.location.href = '/')}>
          返回下单页
        </button>
      </Shell>
    )
  }

  return (
    <Shell title="选择支付方式" subtitle="步骤B：选择支付方式，点击“去支付”" activeNav="create">
      {loading ? <p>加载订单中...</p> : null}
      {error ? <div className="error">{error}</div> : null}

      {order ? (
        <div className="order-created">
          {order.productImage && (
            <div className="product-row">
              <img src={order.productImage} alt={order.productName} />
              <div>
                <strong>{order.productName}</strong>
                <div>数量: {order.quantity}</div>
              </div>
            </div>
          )}
          <div>订单号: {order.orderId}</div>
          <div>支付单号: {order.paymentId}</div>
          <div>商品名称: {order.productName}</div>
          <div>订单金额: {formatMoney(order.fiatAmount, order.fiatCurrency)}</div>
          <div>当前状态: {statusText[order.status] || order.status}</div>
        </div>
      ) : null}

      <div className="method-list">
        {paymentMethods.map((method) => (
          <label key={method.id} className={`method-option ${selectedMethodId === method.id ? 'selected' : ''}`}>
            <input
              type="radio"
              name="paymentMethod"
              checked={selectedMethodId === method.id}
              onChange={() => setSelectedMethodId(method.id)}
            />
            <div>
              <strong>{method.name}</strong>
              <div>{method.desc}</div>
            </div>
          </label>
        ))}
      </div>

      <button type="button" disabled={!order || loading} onClick={goPay}>
        去支付
      </button>
    </Shell>
  )
}

function OrdersPage() {
  const [orders, setOrders] = useState<OrderStatusResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const pageSize = 20

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    setHasMore(true)

    listOrders(pageSize, 0)
      .then((rows) => {
        if (!active) {
          return
        }
        setOrders(rows)
        setHasMore(rows.length === pageSize)
      })
      .catch((err) => {
        if (!active) {
          return
        }
        setError(err instanceof Error ? err.message : '查询订单列表失败')
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  const handleLoadMore = () => {
    if (loadingMore || !hasMore) {
      return
    }
    setLoadingMore(true)
    setError(null)

    listOrders(pageSize, orders.length)
      .then((rows) => {
        setOrders((prev) => [...prev, ...rows])
        setHasMore(rows.length === pageSize)
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : '查询订单列表失败')
      })
      .finally(() => {
        setLoadingMore(false)
      })
  }

  return (
    <Shell title="我的订单" subtitle="最近订单列表" activeNav="orders">
      {loading ? <p>加载订单中...</p> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading && !orders.length ? <div className="order-created">暂无订单，请先下单</div> : null}

      {orders.length ? (
        <>
          <div className="orders-table">
            <div className="orders-head">
              <span>订单号</span>
              <span>商品</span>
              <span>金额</span>
              <span>状态</span>
              <span>更新时间</span>
            </div>
            {orders.map((item) => (
              <div className="orders-row" key={item.orderId}>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => (window.location.href = `/orders/${encodeURIComponent(item.orderId)}`)}
                >
                  {item.orderId}
                </button>
                <span className="orders-product-cell">
                  {item.productImage && (
                    <img src={item.productImage} alt={item.productName} className="orders-product-image" />
                  )}
                  {item.productName}
                </span>
                <span>{formatMoney(item.fiatAmount, item.fiatCurrency)}</span>
                <span>{statusText[item.status] || item.status}</span>
                <span>{new Date(item.lastUpdatedAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
          {hasMore ? (
            <button
              type="button"
              className="secondary-button"
              onClick={handleLoadMore}
              disabled={loadingMore}
              style={{ marginTop: '16px', width: '100%' }}
            >
              {loadingMore ? '加载中...' : '加载更多'}
            </button>
          ) : orders.length >= pageSize ? (
            <div style={{ marginTop: '16px', textAlign: 'center', color: '#666' }}>
              已加载全部订单
            </div>
          ) : null}
        </>
      ) : null}
    </Shell>
  )
}

function OrderDetailPage() {
  const orderId = useMemo(() => {
    const prefix = '/orders/'
    if (!window.location.pathname.startsWith(prefix)) {
      return null
    }
    const raw = window.location.pathname.slice(prefix.length)
    if (!raw) {
      return null
    }
    return decodeURIComponent(raw)
  }, [])

  const [order, setOrder] = useState<OrderStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!orderId) {
      setError('缺少订单号')
      return
    }

    let active = true
    let timer: number | undefined
    let firstLoad = true

    const load = async () => {
      try {
        const detail = firstLoad ? await getOrder(orderId) : await getOrderPaymentStatus(orderId)
        firstLoad = false
        if (!active) {
          return
        }
        setOrder(detail)
        if (!terminalStatuses.has(detail.status)) {
          timer = window.setTimeout(() => {
            void load()
          }, 3000)
        }
      } catch (err) {
        if (!active) {
          return
        }
        setError(err instanceof Error ? err.message : '查询订单失败')
      }
    }

    void load()

    return () => {
      active = false
      if (timer) {
        window.clearTimeout(timer)
      }
    }
  }, [orderId])

  if (!orderId) {
    return (
      <Shell title="订单详情" activeNav="orders">
        <div className="error">缺少订单号</div>
        <button type="button" onClick={() => (window.location.href = '/orders')}>
          返回订单列表
        </button>
      </Shell>
    )
  }

  return (
    <Shell title="订单详情" subtitle="点击订单号进入详情" activeNav="orders">
      {error ? <div className="error">{error}</div> : null}
      {!order ? <p>加载订单详情中...</p> : null}

      {order ? (
        <div className="result-list">
          {order.productImage && (
            <div className="product-row">
              <img src={order.productImage} alt={order.productName} />
              <div>
                <strong>{order.productName}</strong>
                <div>数量: {order.quantity}</div>
              </div>
            </div>
          )}
          <div>订单号: {order.orderId}</div>
          <div>支付单号: {order.paymentId}</div>
          <div>订单状态: {statusText[order.status] || order.status}</div>
          <div>商品名称: {order.productName}</div>
          <div>订单金额: {formatMoney(order.fiatAmount, order.fiatCurrency)}</div>
          <div>最后更新: {new Date(order.lastUpdatedAt).toLocaleString()}</div>
          {(order.status === 'PARTIALLY_PAID' || order.status === 'PROCESSING' || order.status === 'PENDING') ? (
            <button type="button" onClick={() => (window.location.href = order.checkoutUrl)}>
              去支付
            </button>
          ) : null}
          <button type="button" className="secondary-button" onClick={() => (window.location.href = '/orders')}>
            返回订单列表
          </button>
        </div>
      ) : null}
    </Shell>
  )
}

function ResultPage() {
  const query = useMemo(() => new URLSearchParams(window.location.search), [])
  const orderId = query.get('orderId')
  const paymentIdFromQuery = query.get('paymentId') || query.get('paymentNo')
  const statusFromQuery = query.get('status')
  const fromCheckout = query.get('from') === 'checkout'
  const [order, setOrder] = useState<OrderStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!orderId) {
      setError('缺少 orderId，无法查询商户订单')
      return
    }

    let active = true
    let timer: number | undefined
    let firstLoad = true

    const load = async () => {
      try {
        const detail = firstLoad ? await getOrder(orderId) : await getOrderPaymentStatus(orderId)
        firstLoad = false
        if (!active) {
          return
        }
        setOrder(detail)
        if (!terminalStatuses.has(detail.status)) {
          timer = window.setTimeout(() => {
            void load()
          }, 3000)
        }
      } catch (err) {
        if (!active) {
          return
        }
        setError(err instanceof Error ? err.message : '查询订单失败')
      }
    }

    void load()

    return () => {
      active = false
      if (timer) {
        window.clearTimeout(timer)
      }
    }
  }, [orderId])

  if (!orderId) {
    return (
      <Shell title="支付结果" activeNav="orders">
        <div className="result-list">
          <div>支付单号: {paymentIdFromQuery || '-'}</div>
          <div>支付状态: {statusText[statusFromQuery || ''] || statusFromQuery || '-'}</div>
          <div className="error">缺少 orderId，商户无法查询订单详情</div>
          <button type="button" onClick={() => (window.location.href = '/')}>
            返回下单页
          </button>
        </div>
      </Shell>
    )
  }

  const displayOrderId = order?.orderId || orderId
  const displayPaymentId = order?.paymentId || paymentIdFromQuery || '-'
  const displayStatus = order?.status || statusFromQuery || 'PROCESSING'

  return (
    <Shell title="支付结果" subtitle={fromCheckout ? '来源: 收银台回跳' : undefined} activeNav="orders">
      {error ? <div className="error">{error}</div> : null}
      {!order ? <p>正在同步商户订单状态...</p> : null}
      <div className="result-list">
        {order?.productImage && (
          <div className="product-row">
            <img src={order.productImage} alt={order.productName} />
            <div>
              <strong>{order.productName}</strong>
              <div>数量: {order.quantity}</div>
            </div>
          </div>
        )}
        <div>订单号: {displayOrderId}</div>
        <div>支付单号: {displayPaymentId}</div>
        <div>订单状态: {statusText[displayStatus] || displayStatus}</div>
        {order ? (
          <>
            <div>商品名称: {order.productName}</div>
            <div>订单金额: {formatMoney(order.fiatAmount, order.fiatCurrency)}</div>
            <div>最后更新: {new Date(order.lastUpdatedAt).toLocaleString()}</div>
          </>
        ) : null}
        {order?.status === 'PARTIALLY_PAID' || order?.status === 'PROCESSING' || order?.status === 'PENDING' ? (
          <button type="button" onClick={() => (window.location.href = order.checkoutUrl)}>
            去支付
          </button>
        ) : null}
        <button type="button" className="secondary-button" onClick={() => (window.location.href = '/orders')}>
          查看我的订单
        </button>
      </div>
    </Shell>
  )
}

export default function App() {
  const pathname = window.location.pathname
  const query = new URLSearchParams(window.location.search)
  const isCheckoutReturn = pathname === '/' && (query.get('from') === 'checkout' || query.has('orderId') || query.has('paymentId'))

  if (pathname === '/result' || isCheckoutReturn) {
    return <ResultPage />
  }
  if (pathname === '/pay') {
    return <PayPage />
  }
  if (pathname === '/orders') {
    return <OrdersPage />
  }
  if (pathname.startsWith('/orders/')) {
    return <OrderDetailPage />
  }
  return <CreateOrderPage />
}
