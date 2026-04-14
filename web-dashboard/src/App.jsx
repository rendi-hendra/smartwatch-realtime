import { useState, useEffect } from 'react';
import { initializeApp } from "firebase/app";
import { 
  getFirestore, 
  collection, 
  query, 
  orderBy, 
  limit, 
  onSnapshot,
  where
} from "firebase/firestore";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import './App.css';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

// Updated Firebase configuration using smartwatch-fb0ce
const firebaseConfig = {
    apiKey: "AIzaSyCyJ-O61WVsFdj2blKFj4j9J20eZQ4j_6I",
    authDomain: "smartwatch-fb0ce.firebaseapp.com",
    projectId: "smartwatch-fb0ce",
    storageBucket: "smartwatch-fb0ce.firebasestorage.app",
    messagingSenderId: "1047070421051",
    appId: "1:1047070421051:web:placeholder"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

function App() {
  const [currentData, setCurrentData] = useState({ hr: '--', steps: '--', spo2: '--' });
  const [history, setHistory] = useState([]);
  const [filter, setFilter] = useState('live'); // 'live', 'today', 'yesterday', 'week'
  const [stats, setStats] = useState({ avgHr: 0, maxHr: 0 });

  // 1. Live Data Listener (Always keeps the top cards updated)
  useEffect(() => {
    const q = query(collection(db, "sensor_data"), orderBy("timestamp", "desc"), limit(1));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const docs = snapshot.docs.map(doc => doc.data());
      if (docs.length > 0) {
        setCurrentData(docs[0]);
      }
    });
    return () => unsubscribe();
  }, []);

  // 2. History Chart Listener (Based on active tab)
  useEffect(() => {
    let q;
    const coll = collection(db, "sensor_data");
    const now = new Date();

    if (filter === 'live') {
      q = query(coll, orderBy("timestamp", "desc"), limit(40));
    } else if (filter === 'today') {
      const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      q = query(coll, where("timestamp", ">=", startOfToday), orderBy("timestamp", "desc"), limit(300));
    } else if (filter === 'yesterday') {
      const startOfYesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1).getTime();
      const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      q = query(coll, where("timestamp", ">=", startOfYesterday), where("timestamp", "<", startOfToday), orderBy("timestamp", "desc"), limit(300));
    } else if (filter === 'week') {
      const startOfWeek = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).getTime();
      q = query(coll, where("timestamp", ">=", startOfWeek), orderBy("timestamp", "desc"), limit(600));
    }

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const docs = snapshot.docs.map(doc => doc.data());
      
      if (docs.length > 0) {
        setHistory([...docs].reverse()); // Reverse for chronological order on chart
        
        // Calculate Statistics
        const hrs = docs.map(d => d.hr);
        const avg = Math.round(hrs.reduce((a, b) => a + b, 0) / hrs.length);
        const max = Math.max(...hrs);
        setStats({ avgHr: avg, maxHr: max });
      } else {
        setHistory([]);
        setStats({ avgHr: 0, maxHr: 0 });
      }
    });

    return () => unsubscribe();
  }, [filter]);

  // Chart preparation
  const chartData = {
    labels: history.map(h => {
      const d = new Date(h.timestamp);
      // For short intervals (live/today), show just time. Otherwise show date+time.
      if (filter === 'live' || filter === 'today') return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: filter === 'live' ? '2-digit' : undefined });
      return d.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    }),
    datasets: [
      {
        label: 'Heart Rate (BPM)',
        data: history.map(h => h.hr),
        borderColor: '#ff4b5c',
        backgroundColor: 'rgba(255, 75, 92, 0.15)',
        borderWidth: 2,
        pointRadius: filter === 'live' ? 3 : 0, // hide dots for large datasets to look cleaner
        pointHoverRadius: 6,
        tension: 0.3,
        fill: true,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      y: {
        grid: { color: '#2a2d35', borderDash: [5, 5] },
        ticks: { color: '#9aa0a6' },
      },
      x: {
        grid: { display: false },
        ticks: { color: '#9aa0a6', maxTicksLimit: 8 }
      }
    },
    plugins: {
      legend: { display: false },
      tooltip: { mode: 'index', intersect: false }
    }
  };

  return (
    <div className="admin-layout">
      {/* Sidebar Area */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <h2>Mi<span>Admin</span></h2>
        </div>
        <div className="sidebar-menu">
          <div className="menu-item active">📊 Dashboard & Analytics</div>
          <div className="menu-item">👤 Patient List</div>
          <div className="menu-item">🔔 Alerts</div>
          <div className="menu-item">⚙️ Settings</div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <header className="topbar">
          <div className="page-title">
            <h1>Health Monitoring</h1>
            <p>Real-time telemetry and historical analysis</p>
          </div>
          <div className="status-badge">
            <span className="dot"></span> System Online
          </div>
        </header>

        {/* Global Live Stats */}
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-header">
              <span>Current Heart Rate</span>
              <span className="color-hr">❤️</span>
            </div>
            <h2 className="stat-value">{currentData.hr}<span>BPM</span></h2>
          </div>
          
          <div className="stat-card">
            <div className="stat-header">
              <span>Steps Taken Today</span>
              <span className="color-steps">👟</span>
            </div>
            <h2 className="stat-value">{currentData.steps.toLocaleString()}</h2>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <span>Current Blood Oxygen</span>
              <span className="color-spo2">💨</span>
            </div>
            <h2 className="stat-value">{currentData.spo2}<span>%</span></h2>
          </div>
        </div>

        {/* Analytics & History Section */}
        <section className="analytics-section">
          <div className="analytics-header">
            <h3>Heart Rate Analytics</h3>
            <div className="filter-tabs">
              <button className={`filter-btn ${filter === 'live' ? 'active' : ''}`} onClick={() => setFilter('live')}>Live</button>
              <button className={`filter-btn ${filter === 'today' ? 'active' : ''}`} onClick={() => setFilter('today')}>Today</button>
              <button className={`filter-btn ${filter === 'yesterday' ? 'active' : ''}`} onClick={() => setFilter('yesterday')}>Yesterday</button>
              <button className={`filter-btn ${filter === 'week' ? 'active' : ''}`} onClick={() => setFilter('week')}>1 Week</button>
            </div>
          </div>

          {/* Sub-stats for the selected timeframe */}
          <div className="stats-grid" style={{ marginBottom: '1.5rem', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))' }}>
            <div className="stat-card" style={{ padding: '1rem' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem' }}>Average HR</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem' }}>{stats.avgHr}<span>BPM</span></h2>
            </div>
            <div className="stat-card" style={{ padding: '1rem' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem' }}>Peak Max HR</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem' }}>{stats.maxHr}<span>BPM</span></h2>
            </div>
            <div className="stat-card" style={{ padding: '1rem' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem' }}>Data Points</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem' }}>{history.length}</h2>
            </div>
          </div>

          {/* Main Chart */}
          <div className="chart-wrapper">
            <Line data={chartData} options={chartOptions} />
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
