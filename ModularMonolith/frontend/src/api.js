const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function handle(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    // no JSON body (e.g. network-level failure) - fall through
  }
  if (!response.ok) {
    const message = body?.error || body?.reason || `Request failed with status ${response.status}`
    throw new Error(message)
  }
  return body
}

export async function placeOrder(items) {
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ items }),
  })
  return handle(response)
}

export async function cancelOrder(orderId) {
  const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/cancel`, {
    method: 'POST',
  })
  return handle(response)
}

export async function fetchInventory() {
  const response = await fetch(`${API_BASE_URL}/api/inventory`)
  return handle(response)
}

export async function fetchOrders() {
  const response = await fetch(`${API_BASE_URL}/api/orders`)
  return handle(response)
}

export async function fetchNotifications() {
  const response = await fetch(`${API_BASE_URL}/api/notifications`)
  return handle(response)
}
