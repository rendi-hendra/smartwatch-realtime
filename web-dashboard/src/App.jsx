import { useState, useEffect } from 'react';
import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue } from "firebase/database";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
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
  Legend
);

// Firebase configuration using credentials from google-services.json
const firebaseConfig = {
    apiKey: "AIzaSyAIPrwe0hso2CyBpEW0_bVyrTNPor1Orkk",
    authDomain: "smartwatch-e626c.firebaseapp.com",
    databaseURL: "https://smartwatch-e626c-default-rtdb.asia-southeast1.firebasedatabase.app",
    projectId: "smartwatch-e626c",
    storageBucket: "smartwatch-e626c.appspot.com",
    messagingSenderId: "211747187668",
    appId: "1:211747187668:web:placeholder" // Replace with actual Web App ID if different
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

function App() {
  const [healthData, setHealthData] = useState({
    hr: 0,
    steps: 0,
    spo2: 0,
    history: []
  });

  useEffect(() => {
    const dataRef = ref(db, 'smartwatch_data/current');
    
    // Subscribe to Firebase updates
    const unsubscribe = onValue(dataRef, (snapshot) => {
      const data = snapshot.val();
      if (data) {
        setHealthData((prev) => ({
          ...data,
          history: [...prev.history, { t: data.timestamp, hr: data.hr }].slice(-20)
        }));
      }
    });

    return () => unsubscribe();
  }, []);

  const chartData = {
    labels: healthData.history.map(h => new Date(h.t).toLocaleTimeString()),
    datasets: [
      {
        label: 'Heart Rate (BPM)',
        data: healthData.history.map(h => h.hr),
        borderColor: '#ff4b5c',
        backgroundColor: 'rgba(255, 75, 92, 0.2)',
        tension: 0.4,
        fill: true,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    scales: {
      y: {
        beginAtZero: false,
        suggestedMin: 40,
        suggestedMax: 180,
      }
    },
    plugins: {
      legend: {
        display: false,
      }
    }
  };

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <h1>Smartwatch Real-Time Monitor</h1>
        <div className="status">
          <span className="dot"></span> Live Monitoring (via Firebase)
        </div>
      </header>

      <div className="grid-container">
        <div className="card">
          <div className="card-icon">❤️</div>
          <div className="card-content">
            <h3>Heart Rate</h3>
            <p className="value">{healthData.hr} <span>BPM</span></p>
          </div>
        </div>

        <div className="card">
          <div className="card-icon">👟</div>
          <div className="card-content">
            <h3>Total Steps</h3>
            <p className="value">{healthData.steps.toLocaleString()}</p>
          </div>
        </div>

        <div className="card">
          <div className="card-icon">🩸</div>
          <div className="card-content">
            <h3>Oxygen (SpO2)</h3>
            <p className="value">{healthData.spo2}%</p>
          </div>
        </div>
      </div>

      <div className="chart-section">
        <h3>Heart Rate Trend</h3>
        <div className="chart-wrapper">
          <Line data={chartData} options={chartOptions} />
        </div>
      </div>
    </div>
  );
}

export default App;
