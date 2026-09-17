export default function InventoryTable({ inventory }) {
  return (
    <section style={{ marginBottom: '2rem' }}>
      <h2>Inventory</h2>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ textAlign: 'left', borderBottom: '2px solid #ccc' }}>
            <th style={{ padding: '0.4rem' }}>Product</th>
            <th style={{ padding: '0.4rem' }}>Name</th>
            <th style={{ padding: '0.4rem' }}>Stock</th>
          </tr>
        </thead>
        <tbody>
          {inventory.map((item) => (
            <tr
              key={item.productId}
              style={{
                borderBottom: '1px solid #eee',
                background: item.lowStock ? '#fff3cd' : 'transparent',
                color: item.lowStock ? '#8a6100' : 'inherit',
              }}
            >
              <td style={{ padding: '0.4rem' }}>{item.productId}</td>
              <td style={{ padding: '0.4rem' }}>{item.name}</td>
              <td style={{ padding: '0.4rem' }}>
                {item.stock} {item.lowStock && <strong>(low stock)</strong>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
