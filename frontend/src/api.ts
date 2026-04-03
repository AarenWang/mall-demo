export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface CreateOrderRequest {
  fiatCurrency: string
  fiatAmount: string
  productName: string
  productImage: string
  quantity: number
}

export interface OrderStatusResponse {
  orderId: string
  paymentId: string
  checkoutUrl: string
  status: string
  productName: string
  productImage: string
  quantity: number
  fiatCurrency: string
  fiatAmount: string
  lastUpdatedAt: number
}

const API_BASE = import.meta.env.VITE_MALL_API_BASE || 'http://localhost:18080'
const MERCHANT_ID = import.meta.env.VITE_MERCHANT_ID || 'merchant_a'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Merchant-Id': MERCHANT_ID,
      ...(init?.headers || {})
    }
  })
  const body: ApiResponse<T> = await response.json()
  if (body.code !== 0) {
    throw new Error(body.message || `API error code=${body.code}`)
  }
  return body.data
}

export function createOrder(payload: CreateOrderRequest) {
  return request<{ orderId: string; paymentId: string; checkoutUrl: string }>('/api/mall/orders', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getOrder(orderId: string) {
  return request<OrderStatusResponse>(`/api/mall/orders/${encodeURIComponent(orderId)}`)
}

export function getOrderPaymentStatus(orderId: string) {
  return request<OrderStatusResponse>(
    `/api/mall/orders/${encodeURIComponent(orderId)}/payment-status`
  )
}

export function listOrders(limit = 20, offset = 0) {
  return request<OrderStatusResponse[]>(
    `/api/mall/orders?limit=${encodeURIComponent(String(limit))}&offset=${encodeURIComponent(String(offset))}`
  )
}
