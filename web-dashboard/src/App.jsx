import { useState, useEffect, useMemo } from 'react';
import { initializeApp } from "firebase/app";
import { 
  getFirestore, collection, query, orderBy, limit, onSnapshot, where
} from "firebase/firestore";
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import './App.css';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler);

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

const formatDateLabel = (date) => {
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  
  if (date.toDateString() === today.toDateString()) return "Today";
  if (date.toDateString() === yesterday.toDateString()) return "Yesterday";
  return date.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
};

const getPastDays = (numDays) => {
  const days = [];
  for (let i = 0; i < numDays; i++) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push(d);
  }
  return days;
};

const metricConfig = {
  hr: { label: 'Heart Rate (BPM)', color: '#ff4b5c', bgColor: 'rgba(255, 75, 92, 0.15)', icon: '❤️', format: (val) => Number(val).toFixed(0) },
  steps: { label: 'Total Steps', color: '#10b981', bgColor: 'rgba(16, 185, 129, 0.15)', icon: '👟', format: (val) => Number(val).toLocaleString() },
  spo2: { label: 'Blood Oxygen (%)', color: '#a8edea', bgColor: 'rgba(168, 237, 234, 0.15)', icon: '💨', format: (val) => Number(val).toFixed(1) }
};

function App() {
  const [currentData, setCurrentData] = useState({ hr: '--', steps: '--', spo2: '--' });
  const [historyDocs, setHistoryDocs] = useState([]);
  
  // viewMode string format: 'live' | timestamp representing start of that day
  const [viewMode, setViewMode] = useState('live'); 
  const [activeMetric, setActiveMetric] = useState('hr'); // 'hr', 'steps', 'spo2'
  const [stats, setStats] = useState({ avg: 0, max: 0 });

  // Generate sidebar dates once
  const pastDays = useMemo(() => getPastDays(7), []);

  useEffect(() => {
    const q = query(collection(db, "sensor_data"), orderBy("timestamp", "desc"), limit(1));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const docs = snapshot.docs.map(doc => doc.data());
      if (docs.length > 0) setCurrentData(docs[0]);
    });
    return () => unsubscribe();
  }, []);

  useEffect(() => {
    let q;
    const coll = collection(db, "sensor_data");

    if (viewMode === 'live') {
      q = query(coll, orderBy("timestamp", "desc"), limit(40));
    } else {
      const startOfDay = new Date(parseInt(viewMode, 10));
      startOfDay.setHours(0,0,0,0);
      const startTs = startOfDay.getTime();
      
      const endOfDay = new Date(startOfDay);
      endOfDay.setDate(endOfDay.getDate() + 1);
      const endTs = endOfDay.getTime();

      q = query(coll, where("timestamp", ">=", startTs), where("timestamp", "<", endTs), orderBy("timestamp", "desc"), limit(300));
    }

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const docs = snapshot.docs.map(doc => doc.data());
      setHistoryDocs([...docs].reverse()); 
    });

    return () => unsubscribe();
  }, [viewMode]);

  useEffect(() => {
    if (historyDocs.length > 0) {
      let values = [];
      if (activeMetric === 'hr') values = historyDocs.map(d => Number(d.hr));
      if (activeMetric === 'steps') values = historyDocs.map(d => Number(d.steps));
      if (activeMetric === 'spo2') values = historyDocs.map(d => Number(d.spo2 || 0));

      const validValues = values.filter(v => !isNaN(v) && v !== 0);
      if (validValues.length > 0) {
        const sum = validValues.reduce((a, b) => a + b, 0);
        const avg = sum / validValues.length;
        const max = Math.max(...validValues);
        setStats({ avg, max });
      } else {
        setStats({ avg: 0, max: 0 });
      }
    } else {
      setStats({ avg: 0, max: 0 });
    }
  }, [historyDocs, activeMetric]);

  const config = metricConfig[activeMetric];

  const chartData = {
    labels: historyDocs.map(h => {
      const d = new Date(h.timestamp);
      return viewMode === 'live' 
        ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
        : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }),
    datasets: [
      {
        label: config.label,
        data: historyDocs.map(h => {
           if (activeMetric === 'hr') return h.hr;
           if (activeMetric === 'steps') return h.steps;
           if (activeMetric === 'spo2') return h.spo2;
           return 0;
        }),
        borderColor: config.color,
        backgroundColor: config.bgColor,
        borderWidth: 2,
        pointRadius: viewMode === 'live' ? 4 : 0, // clean look for long histories
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
          <div className="section-label">Monitoring</div>
          <div 
            className={`menu-item ${viewMode === 'live' ? 'active' : ''}`}
            onClick={() => setViewMode('live')}
          >
            🔴 Live Dashboard
          </div>
          
          <div className="section-label">Daily History</div>
          {pastDays.map((date, idx) => {
            const timestampStr = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime().toString();
            return (
              <div 
                key={idx}
                className={`menu-item ${viewMode === timestampStr ? 'active-history' : ''}`}
                onClick={() => setViewMode(timestampStr)}
              >
                📅 {formatDateLabel(date)}
              </div>
            );
          })}
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <header className="topbar">
          <div className="page-title">
            <h1>Activity Monitoring</h1>
            <p>{viewMode === 'live' ? 'Real-time sensor telemetry' : `Displaying summary data for ${formatDateLabel(new Date(parseInt(viewMode)))}`}</p>
          </div>
          <div className="status-badge">
            <span className={viewMode === 'live' ? "dot" : ""}></span> 
            {viewMode === 'live' ? 'Live System Online' : 'Historical Data View'}
          </div>
        </header>

        {/* Global Live Stats (Always up to date) */}
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
              <span>Current Daily Steps</span>
              <span className="color-steps">👟</span>
            </div>
            <h2 className="stat-value">{Number(currentData.steps).toLocaleString()}</h2>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <span>Current Blood Oxygen</span>
              <span className="color-spo2">💨</span>
            </div>
            <h2 className="stat-value">{Number(currentData.spo2 || 0).toFixed(1)}<span>%</span></h2>
          </div>
        </div>

        {/* Analytics Section with Metric Tabs */}
        <section className="analytics-section">
          <div className="analytics-header">
            <h3>Detailed Chart Analysis</h3>
            <div className="metric-tabs">
              <button 
                className={`metric-btn tab-hr ${activeMetric === 'hr' ? 'active' : ''}`} 
                onClick={() => setActiveMetric('hr')}
              >
                ❤️ Heart Rate
              </button>
              <button 
                className={`metric-btn tab-steps ${activeMetric === 'steps' ? 'active' : ''}`} 
                onClick={() => setActiveMetric('steps')}
              >
                👟 Steps
              </button>
              <button 
                className={`metric-btn tab-spo2 ${activeMetric === 'spo2' ? 'active' : ''}`} 
                onClick={() => setActiveMetric('spo2')}
              >
                💨 Oxygen
              </button>
            </div>
          </div>

          <div className="stats-grid" style={{ marginBottom: '1.5rem', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))' }}>
            <div className="stat-card" style={{ padding: '1rem', backgroundColor: config.bgColor, borderColor: 'transparent' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem', color: '#fff' }}>Average {config.label.split(' ')[0]}</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem', color: config.color }}>
                {config.format(stats.avg)}
              </h2>
            </div>
            <div className="stat-card" style={{ padding: '1rem' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem' }}>Peak {config.label.split(' ')[0]}</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem' }}>
                {config.format(stats.max)}
              </h2>
            </div>
            <div className="stat-card" style={{ padding: '1rem' }}>
              <div className="stat-header" style={{ marginBottom: '0.5rem' }}>Data Samples</div>
              <h2 className="stat-value" style={{ fontSize: '1.75rem' }}>{historyDocs.length}</h2>
            </div>
          </div>

          <div className="chart-wrapper">
            <Line data={chartData} options={chartOptions} />
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
