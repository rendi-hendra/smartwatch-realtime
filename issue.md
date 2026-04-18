## 📌 Deskripsi

Dokumen ini berisi perencanaan implementasi **struktur database Firebase Realtime Database** untuk menyimpan data kesehatan dari Health Connect.

Sistem menggunakan pendekatan:

* Identifikasi berdasarkan `deviceId`
* Penyimpanan data berbasis timestamp
* Optimized untuk kebutuhan dashboard (read cepat & scalable)

---

## 🎯 Tujuan

1. Menyimpan data kesehatan secara terstruktur dan tidak duplikat
2. Memudahkan query data per device
3. Mendukung visualisasi data (chart, statistik) di web
4. Menjaga performa database tetap optimal

---

## 📦 Struktur Data yang Harus Dibuat

AI agent harus mengimplementasikan struktur berikut:

```json
{
  "devices": {
    "{deviceId}": {
      "deviceName": "string",
      "lastSync": number
    }
  },
  "health_data": {
    "{deviceId}": {
      "{timestamp}": {
        "heartRate": number,
        "steps": number,
        "oxygenSaturation": number,
        "heartRateVariabilityRmssd": number,
        "activeCaloriesBurned": number
      }
    }
  }
}
```

---

## 🛠️ Tahapan Implementasi

### STEP 1 — Kirim ke database setiap 1 menit
1. Rubah menjadi setiap 1 menit saat kirim ke database

---

### STEP 2 — Simpan Data Device

Saat aplikasi pertama kali dijalankan:

1. Generate `deviceId` (UUID)
2. Simpan ke local storage
3. Kirim ke Firebase:

```json
devices/{deviceId}
```

Isi:

* deviceName
* lastSync (timestamp sekarang)

---

### STEP 3 — Implementasi Penyimpanan Data Kesehatan

Saat melakukan sync:

1. Ambil waktu sekarang → gunakan sebagai `timestamp`
2. Simpan data ke path:

```plaintext
health_data/{deviceId}/{timestamp}
```

3. Isi data:

* heartRate
* steps
* oxygenSaturation
* heartRateVariabilityRmssd
* activeCaloriesBurned


---

### STEP 4 — Update lastSync

Setelah data berhasil dikirim:

```plaintext
devices/{deviceId}/lastSync
```

Update dengan timestamp terbaru

---

### STEP 5 — Hindari Duplikasi Data

1. Gunakan `lastSyncTime` dari local storage
2. Ambil data dari Health Connect setelah waktu tersebut
3. Jangan kirim data lama

---

### STEP 6 — Optimasi Query (Penting)

Pastikan:

* Data diakses berdasarkan `deviceId`
* Tidak menggunakan array
* Menggunakan timestamp sebagai key

---

### STEP 7 — Error Handling

Tambahkan:

* Retry jika gagal kirim data
* Logging error
* Validasi data sebelum dikirim

---

## ⚠️ Aturan Penting

* Jangan gunakan array di Realtime Database
* Jangan overwrite data lama
* Gunakan timestamp sebagai key unik
* Pisahkan node `devices` dan `health_data`
* Semua data harus memiliki `deviceId`

---

## 🚀 Expected Result

* Data tersimpan rapi per device
* Tidak ada data duplikat
* Data mudah diambil untuk dashboard
* Sistem siap untuk scaling

---

## 📈 Optional Improvement

* Pisahkan data per tipe (heart_rate, steps, dll)
* Tambahkan indexing untuk performa
* Tambahkan fitur agregasi (daily summary)
