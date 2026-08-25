/*
  SerialTest - Arduino Mobile Workshop core example.
  Streams an incrementing counter over the serial port at 9600 baud so you can
  open the in-app Serial Monitor and confirm the USB link is working.
*/
int counter = 0;

void setup() {
  Serial.begin(9600);
  while (!Serial) {
    ; // wait for the serial port to connect (needed for native USB boards)
  }
  Serial.println("SerialTest started");
}

void loop() {
  Serial.print("tick ");
  Serial.println(counter);
  counter++;
  delay(500);
}
