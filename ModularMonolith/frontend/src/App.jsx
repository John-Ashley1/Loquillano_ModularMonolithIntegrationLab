import { useEffect, useState, useCallback } from 'react'
import { placeOrder, cancelOrder, fetchInventory, fetchOrders, fetchNotifications } from './api'
import Cart from './components/Cart'
import InventoryTable from './components/InventoryTable'
import OrderHistory from './components/OrderHistory'
import NotificationsFeed from './components/NotificationsFeed'

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)
  const [lastResult, setLastResult] = useState(null)
  const [error, setError] = useState(null)

  const refreshAll = useCallback(async () => {
    try {
      const [inv, ords, notes] = await Promise.all([fetchInventory(), fetchOrders(), fetchNotifications()])
      setInventory(inv)
      setOrders(ords)
      setNotifications(notes)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    refreshAll()
  }, [refreshAll])

  async function handleSubmitOrder(items) {
    setSubmitting(true)
    setError(null)
    try {
      const result = await placeOrder(items)
      setLastResult(result)
      await refreshAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(orderId) {
    setCancellingId(orderId)
    setError(null)
    try {
      await cancelOrder(orderId)
      await refreshAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '2rem auto', fontFamily: 'sans-serif', padding: '0 1rem' }}>
      <h1>Order & Inventory Dashboard</h1>

      {error && <p style={{ color: 'red' }}>Error: {error}</p>}

      <Cart onSubmitOrder={handleSubmitOrder} submitting={submitting} result={lastResult} />
      <InventoryTable inventory={inventory} />
      <OrderHistory orders={orders} onCancel={handleCancel} cancellingId={cancellingId} />
      <NotificationsFeed notifications={notifications} />
    </div>
  )
}
