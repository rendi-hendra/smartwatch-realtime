const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

io.on('connection', (socket) => {
    console.log('Client connected:', socket.id);

    // Listen for data from Smartwatch
    socket.on('sensor_data', (data) => {
        console.log('Received sensor data:', data);
        // Broadcast to Web Dashboard
        io.emit('web_dashboard_update', data);
    });

    socket.on('disconnect', () => {
        console.log('Client disconnected:', socket.id);
    });
});

// const PORT = process.env.PORT ||  "http://192.168.100.17:3000";
// server.listen(PORT, '0.0.0.0', () => {
//     console.log(`Backend server running on port ${PORT}`);
// });

const PORT = process.env.PORT || 3000;

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Backend server running on port ${PORT}`);
});