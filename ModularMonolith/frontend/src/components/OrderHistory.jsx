export default function OrderHistory({ orders, onCancel, cancellingId }) {
  return (
    <section style={{ marginBottom: '2rem' }}>
      <h2>Order History</h2>
      {orders.length === 0 && <p>No orders yet.</p>}
      <ul style={{ listStyle: 'none', padding: 0 }}>
        {orders.map((order) => (
          <li
            key={order.orderId}
            style={{
              border: '1px solid #ddd',
              borderRadius: 8,
              padding: '0.75rem',
              marginBottom: '0.5rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong>Order #{order.orderId}</strong> —{' '}
                <span
                  style={{
                    color:
                      order.status === 'CONFIRMED'
                        ? 'green'
                        : order.status === 'REJECTED'
                        ? 'crimson'
                        : '#888',
                  }}
                >
                  {order.status}
                </span>
              </div>
              {order.status === 'CONFIRMED' && (
                <button
                  onClick={() => onCancel(order.orderId)}
                  disabled={cancellingId === order.orderId}
                  style={{ padding: '0.3rem 0.8rem' }}
                >
                  {cancellingId === order.orderId ? 'Cancelling…' : 'Cancel'}
                </button>
              )}
            </div>

            {order.reason && <p style={{ margin: '0.4rem 0 0' }}>Reason: {order.reason}</p>}

            <ul style={{ margin: '0.4rem 0 0', paddingLeft: '1.2rem' }}>
              {order.items.map((item, idx) => (
                <li key={idx}>
                  {item.productId} × {item.quantity}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  )
}
