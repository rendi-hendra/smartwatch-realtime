const { io } = require("socket.io-client");

const socket = io("http://localhost:3000");

socket.on("connect", () => {
    console.log("Connected to backend as Smartwatch Simulator");
    
    // Start sending data every 5 seconds
    setInterval(() => {
        const data = {
            hr: Math.floor(Math.random() * (100 - 60 + 1)) + 60,
            steps: Math.floor(Math.random() * 10000),
            spo2: (Math.random() * (100 - 95) + 95).toFixed(1),
            timestamp: Date.now()
        };
        
        console.log("Sending simulated data:", data);
        socket.emit("sensor_data", data);
    }, 5000);
});

socket.on("disconnect", () => {
    console.log("Disconnected from backend");
});

socket.on("connect_error", (err) => {
    console.error("Connection error:", err.message);
});
