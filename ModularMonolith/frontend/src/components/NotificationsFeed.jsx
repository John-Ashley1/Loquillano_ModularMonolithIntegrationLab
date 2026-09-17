export default function NotificationsFeed({ notifications }) {
  return (
    <section style={{ marginBottom: '2rem' }}>
      <h2>Activity Feed</h2>
      {notifications.length === 0 && <p>No notifications yet.</p>}
      <ul style={{ listStyle: 'none', padding: 0 }}>
        {notifications.map((n) => (
          <li
            key={n.notificationId}
            style={{
              padding: '0.5rem 0.75rem',
              borderLeft: n.message.startsWith('Reorder needed') ? '4px solid #d97706' : '4px solid #ccc',
              marginBottom: '0.4rem',
              background: '#fafafa',
            }}
          >
            {n.message}
          </li>
        ))}
      </ul>
    </section>
  )
}
