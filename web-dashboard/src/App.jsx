import { useState, useEffect } from 'react';
import { initializeApp } from "firebase/app";
import { 
  getFirestore, 
  collection, 
  query, 
  orderBy, 
  limit, 
  onSnapshot 
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
  const [currentData, setCurrentData] = useState({
    hr: 0,
    steps: 0,
    spo2: 0,
    timestamp: Date.now()
  });
  const [history, setHistory] = useState([]);

  useEffect(() => {
    // Listen to the latest 20 readings from Firestore
    const q = query(
      collection(db, "sensor_data"),
      orderBy("timestamp", "desc"),
      limit(20)
    );
    
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const docs = snapshot.docs.map(doc => doc.data());
      
      if (docs.length > 0) {
        // Latest document is the current state
        setCurrentData(docs[0]);
        // All documents (reversed for temporal order) are the history
        setHistory([...docs].reverse());
      }
    });

    return () => unsubscribe();
  }, []);

  const chartData = {
    labels: history.map(h => new Date(h.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })),
    datasets: [
      {
        label: 'Heart Rate Trend',
        data: history.map(h => h.hr),
        borderColor: '#ff6700',
        backgroundColor: 'rgba(255, 103, 0, 0.1)',
        borderWidth: 3,
        pointRadius: 4,
        pointBackgroundColor: '#ff6700',
        tension: 0.4,
        fill: true,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      y: {
        grid: { color: 'rgba(255, 255, 255, 0.05)' },
        ticks: { color: '#9aa0a6', font: { family: 'Plus Jakarta Sans' } },
        beginAtZero: false,
      },
      x: {
        grid: { display: false },
        ticks: { color: '#9aa0a6', font: { family: 'Plus Jakarta Sans' } }
      }
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1a1a1a',
        titleFont: { family: 'Plus Jakarta Sans' },
        bodyFont: { family: 'Plus Jakarta Sans' },
        padding: 12,
        cornerRadius: 12,
        displayColors: false
      }
    }
  };

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <h1>Mi <span>Watch</span> Monitor</h1>
        <div className="status-badge">
          <span className="dot"></span> Live Syncing with Firestore
        </div>
      </header>

      <div className="grid-container">
        {/* Heart Rate Card */}
        <div className="premium-card card-hr">
          <div className="card-icon">💓</div>
          <div className="card-label">Heart Rate</div>
          <h2 className="card-value">{currentData.hr}<span>BPM</span></h2>
        </div>

        {/* Steps Card */}
        <div className="premium-card card-steps">
          <div className="card-icon">🏃</div>
          <div className="card-label">Daily Steps</div>
          <h2 className="card-value">{currentData.steps.toLocaleString()}<span>STEPS</span></h2>
        </div>

        {/* SpO2 Card */}
        <div className="premium-card card-spo2">
          <div className="card-icon">🫁</div>
          <div className="card-label">Blood Oxygen</div>
          <h2 className="card-value">{currentData.spo2}<span>%</span></h2>
        </div>
      </div>

      <div className="chart-section">
        <h3>Health Analytics Trend</h3>
        <div className="chart-wrapper">
          <Line data={chartData} options={chartOptions} />
        </div>
      </div>
    </div>
  );
}

export default App;
