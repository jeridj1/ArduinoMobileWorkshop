/*
  Blink - Arduino Mobile Workshop core example.
  Blinks the built-in LED on most Arduino boards. Use this template to test
  that your board is wired up and flashing correctly.
*/
#define LED_PIN 13

void setup() {
  pinMode(LED_PIN, OUTPUT);
}

void loop() {
  digitalWrite(LED_PIN, HIGH);
  delay(1000);
  digitalWrite(LED_PIN, LOW);
  delay(1000);
}
