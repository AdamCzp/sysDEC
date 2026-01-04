#include <SoftwareSerial.h>

#define MQ7_PIN A0        // wejście analogowe czujnika MQ-7
#define BUZZER_PIN 8      // pin buzzera
#define BT_RX 10          // Arduino odbiera z TX HC-05
#define BT_TX 11          // Arduino wysyła do RX HC-05

SoftwareSerial btSerial(BT_RX, BT_TX);

int threshold = 200;     // próg alarmu (dostosujesz po kalibracji)
bool alarmActive = false; // stan alarmu

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH); // buzzer wyłączony (bo aktywny na LOW)
  Serial.println("System start - monitoring CO");
  btSerial.println("System start - monitoring CO");
}

void loop() {
  int sensorValue = analogRead(MQ7_PIN);
  float voltage = sensorValue * (5.0 / 1023.0);
  int ppm = voltage * 100; // uproszczony przelicznik

  // Wysyłamy tylko ppm do aplikacji
  Serial.print(ppm);
  Serial.println(" ppm");
  // W loop() w Arduino
btSerial.print(ppm);
btSerial.print(" ppm");
if (alarmActive) { // jeśli alarm jest aktywny w Arduino
    btSerial.println(" ALARM"); // dodaj "ALARM" do wiadomości
} else {
    btSerial.println(""); // lub po prostu znak nowej linii
}
  // Alarm włącza się
  if (ppm > threshold && !alarmActive) {
    digitalWrite(BUZZER_PIN, LOW); // włącz buzzer
    alarmActive = true;
  }

  // Alarm wyłącza się i 2x pika
  if (ppm <= threshold && alarmActive) {
    digitalWrite(BUZZER_PIN, HIGH); // wyłącz buzzer
    alarmActive = false;

    // 2 krótkie piknięcia sygnału "OK"
    for (int i = 0; i < 3; i++) {
      digitalWrite(BUZZER_PIN, LOW);
      delay(300);
      digitalWrite(BUZZER_PIN, HIGH);
      delay(300);
    }
  }

  delay(500); // wysyłaj co 500 ms
}
