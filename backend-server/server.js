const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');

// IMPORTANT: Ensure serviceAccountKey.json is present in the backend-server directory.
// You can download this from Firebase Console -> Project Settings -> Service Accounts.
let serviceAccount;
try {
    serviceAccount = require("./serviceAccountKey.json");
} catch (e) {
    console.warn("WARNING: serviceAccountKey.json not found. Firebase Admin will not be initialized correctly.");
}

const app = express();
app.use(cors());
app.use(express.json()); // Essential for parsing POST body

if (serviceAccount) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: "https://smartwatch-e626c-default-rtdb.asia-southeast1.firebasedatabase.app" // Regional URL
    });
    console.log("Firebase Admin initialized.");
}

const db = admin.apps.length > 0 ? admin.database() : null;

// Endpoint for Smartwatch to send data
app.post('/api/sensor-data', async (req, res) => {
    const data = req.body;
    console.log('Received sensor data via POST:', data);

    if (!db) {
        return res.status(500).send({ error: "Firebase not initialized" });
    }

    try {
        // Update the current state in Firebase
        await db.ref('smartwatch_data/current').set({
            ...data,
            server_timestamp: admin.database.ServerValue.TIMESTAMP
        });
        res.status(200).send({ message: "Data synchronized to Firebase" });
    } catch (error) {
        console.error("Error writing to Firebase:", error);
        res.status(500).send({ error: "Failed to write to Firebase" });
    }
});

const PORT = process.env.PORT || 3000;

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Backend server running on port ${PORT}`);
});