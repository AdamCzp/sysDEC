package com.example.codetector;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_NOTIFICATIONS = 3;
    private static final String CHANNEL_ID = "CO_DETECTOR_CHANNEL";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;

    private TextView textViewPPM, textViewStatus;
    private Button buttonConnect, buttonDisconnect;

    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewPPM = findViewById(R.id.textViewPPM);
        textViewStatus = findViewById(R.id.textViewStatus);
        buttonConnect = findViewById(R.id.buttonConnect);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        createNotificationChannel();

        buttonConnect.setOnClickListener(v -> connectToHC05());
        buttonDisconnect.setOnClickListener(v -> disconnectFromHC05());
    }

    private void connectToHC05() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Brak modułu Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Poproś użytkownika o włączenie Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // Sprawdzenie i żądanie uprawnień (lokalizacja + BT na Androidzie 12+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))) {

            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions = new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                };
            } else {
                permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            }

            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
            return;
        }

        // Szukamy sparowanego HC-05
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice hc05 = null;
        for (BluetoothDevice device : pairedDevices) {
            if ("HC-05".equals(device.getName())) {
                hc05 = device;
                break;
            }
        }

        if (hc05 == null) {
            Toast.makeText(this, "Nie znaleziono HC-05", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothDevice finalHc05 = hc05;
        new Thread(() -> {
            try {
                bluetoothSocket = finalHc05.createRfcommSocketToServiceRecord(
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();

                isConnected = true;
                runOnUiThread(() -> {
                    textViewStatus.setText("Status: połączono");
                    textViewStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                });

                listenForData();

            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Błąd połączenia: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void listenForData() {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while (isConnected && (line = reader.readLine()) != null) {
                    String received = line.trim();

                    runOnUiThread(() -> {
                        if (received.contains("ppm")) {
                            try {
                                String[] parts = received.split(" ");
                                if (parts.length >= 2) {
                                    String ppmValue = parts[0]; // pierwsza liczba
                                    textViewPPM.setText(ppmValue + " ppm");

                                    if (received.contains("ALARM")) {
                                        showNotification("Zbyt wysokie stężenie CO!");
                                        textViewPPM.setBackgroundResource(android.R.color.holo_red_light);
                                    } else {
                                        textViewPPM.setBackgroundResource(R.drawable.circle_background);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Utracono połączenie", Toast.LENGTH_SHORT).show();
                    textViewStatus.setText("Status: rozłączono");
                    textViewStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                });
                isConnected = false;
            }
        }).start();
    }

    private void disconnectFromHC05() {
        try {
            isConnected = false;
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            runOnUiThread(() -> {
                textViewStatus.setText("Status: rozłączono");
                textViewStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "CO Detector Channel";
            String description = "Powiadomienia o wysokim stężeniu CO";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(String message) {
        // Android 13+ wymaga runtime permission dla powiadomień
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("CO Detector")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1, builder.build());
        }
    }

    // Obsługa odpowiedzi na żądania uprawnień
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS || requestCode == REQUEST_NOTIFICATIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                connectToHC05();
            } else {
                Toast.makeText(this, "Wymagane uprawnienia Bluetooth/lokalizacji/powiadomień", Toast.LENGTH_LONG).show();
            }
        }
    }
}

