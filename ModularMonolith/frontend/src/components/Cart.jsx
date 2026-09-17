import { useState } from 'react'

const PRODUCTS = [
  { productId: 'P100', label: 'P100 — Wireless Mouse' },
  { productId: 'P200', label: 'P200 — Mechanical Keyboard' },
  { productId: 'P300', label: 'P300 — USB-C Hub' },
]

export default function Cart({ onSubmitOrder, submitting, result }) {
  const [productId, setProductId] = useState(PRODUCTS[0].productId)
  const [quantity, setQuantity] = useState(1)
  const [cartItems, setCartItems] = useState([])

  function addToCart(e) {
    e.preventDefault()
    const qty = Number(quantity)
    if (qty < 1) return

    setCartItems((prev) => {
      const existing = prev.find((item) => item.productId === productId)
      if (existing) {
        return prev.map((item) =>
          item.productId === productId ? { ...item, quantity: item.quantity + qty } : item
        )
      }
      return [...prev, { productId, quantity: qty }]
    })
  }

  function removeFromCart(id) {
    setCartItems((prev) => prev.filter((item) => item.productId !== id))
  }

  function labelFor(id) {
    return PRODUCTS.find((p) => p.productId === id)?.label ?? id
  }

  async function handleSubmit() {
    if (cartItems.length === 0) return
    await onSubmitOrder(cartItems)
    setCartItems([])
  }

  return (
    <section style={{ marginBottom: '2rem' }}>
      <h2>Place an Order</h2>

      <form onSubmit={addToCart} style={{ display: 'flex', gap: '0.5rem', alignItems: 'end', flexWrap: 'wrap' }}>
        <div>
          <label htmlFor="product">Product</label>
          <br />
          <select id="product" value={productId} onChange={(e) => setProductId(e.target.value)} style={{ padding: '0.4rem' }}>
            {PRODUCTS.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="quantity">Quantity</label>
          <br />
          <input
            id="quantity"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            style={{ padding: '0.4rem', width: '5rem' }}
          />
        </div>

        <button type="submit" style={{ padding: '0.5rem 1rem' }}>
          Add to cart
        </button>
      </form>

      {cartItems.length > 0 && (
        <div style={{ marginTop: '1rem' }}>
          <h3 style={{ marginBottom: '0.5rem' }}>Cart</h3>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {cartItems.map((item) => (
              <li
                key={item.productId}
                style={{ display: 'flex', justifyContent: 'space-between', padding: '0.3rem 0', borderBottom: '1px solid #eee' }}
              >
                <span>
                  {labelFor(item.productId)} × {item.quantity}
                </span>
                <button onClick={() => removeFromCart(item.productId)} style={{ padding: '0.2rem 0.6rem' }}>
                  Remove
                </button>
              </li>
            ))}
          </ul>

          <button onClick={handleSubmit} disabled={submitting} style={{ padding: '0.6rem 1.2rem', marginTop: '0.5rem' }}>
            {submitting ? 'Submitting…' : 'Submit Order'}
          </button>
        </div>
      )}

      {result && (
        <div
          style={{
            marginTop: '1rem',
            border: '1px solid #ccc',
            borderRadius: 8,
            padding: '1rem',
            background: result.status === 'CONFIRMED' ? '#e6ffed' : '#ffe6e6',
          }}
        >
          <h3 style={{ margin: '0 0 0.5rem' }}>
            {result.status} {result.orderId ? `(Order #${result.orderId})` : ''}
          </h3>
          {result.reason && (
            <p>
              <strong>Reason:</strong> {result.reason}
            </p>
          )}
          {result.items && (
            <ul>
              {result.items.map((item) => (
                <li key={item.productId}>
                  {labelFor(item.productId)}: {item.outcome}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

export { PRODUCTS }
