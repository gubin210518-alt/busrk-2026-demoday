/*
  부품 점검 코드 (실내용)  -  TEAM 부스럭
  ==========================================
  실내에서 부품이 하나씩 제대로 연결됐는지 확인하는 코드예요.
  켜면 전체 점검을 한 번 자동으로 하고, 그다음엔 "실시간 보기"로 바뀌어요.

  시리얼 모니터(115200)에서 숫자를 보내면 그 부품만 다시 점검해요.
    1 : 온도센서 (개수, 주소, 온도)
    2 : 신호등 LED (빨강 -> 노랑 -> 초록)
    3 : 부저 (LOW일 때, HIGH일 때 각각 1초)
    4 : 서보 댐퍼 (0도 -> 90도 -> 0도)
    5 : 팬 (3초 켜기)
    6 : 리드스위치 (자석 대고 떼기)
    0 : 전체 다시 점검
  ※ 시리얼 모니터 아래 줄 끝 설정은 아무거나 괜찮아요.

  실시간 보기 (OLED + 시리얼 2초마다)
    두 센서의 온도, 뚜껑 상태(D27 값)를 보여 줘요.
    센서 하나를 손으로 꽉 쥐면 그 센서 온도만 올라가요.
    -> 그 센서가 몇 번인지 알 수 있어요(식품칸/냉기실 구분).

  필요한 라이브러리: OneWire, DallasTemperature, ESP32Servo,
                    Adafruit SSD1306, Adafruit GFX Library
*/

#include <OneWire.h>
#include <DallasTemperature.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

const int PIN_DS = 4, PIN_SERVO = 18, PIN_FAN = 19;
const int PIN_REED = 27, PIN_LED_R = 26, PIN_LED_Y = 33, PIN_LED_G = 32, PIN_BUZ = 25;

OneWire oneWire(PIN_DS);
DallasTemperature sensors(&oneWire);
Servo damper;
Adafruit_SSD1306 oled(128, 64, &Wire, -1);
bool oledOk = false;

int nSensors = 0;
DeviceAddress addr[4];   // 최대 4개까지 확인

// 부저는 테스트할 때만 켜고, 평소엔 소리가 안 나게 핀을 입력으로 둬요.
// (LOW에서 울리는 모듈이든 HIGH에서 울리는 모듈이든 조용해요)
void buzzerQuiet() { pinMode(PIN_BUZ, INPUT); }

void oledMsg(const char* line1, const char* line2 = "") {
  if (!oledOk) return;
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);
  oled.setTextSize(1);
  oled.setCursor(0, 0);  oled.print("PARTS CHECK");
  oled.setTextSize(2);
  oled.setCursor(0, 20); oled.print(line1);
  oled.setTextSize(1);
  oled.setCursor(0, 48); oled.print(line2);
  oled.display();
}

void printAddr(const DeviceAddress a) {
  Serial.print("{");
  for (int i = 0; i < 8; i++) {
    Serial.printf("0x%02X", a[i]);
    if (i < 7) Serial.print(", ");
  }
  Serial.print("}");
}

// ---------------- 1. 온도센서 ----------------
void checkTemp() {
  Serial.println("\n[1] 온도센서 점검");
  oledMsg("TEMP", "searching...");
  sensors.begin();
  nSensors = min((int)sensors.getDeviceCount(), 4);
  Serial.printf("  찾은 센서: %d개 (2개가 정상)\n", nSensors);
  if (nSensors == 0) {
    Serial.println("  -> 하나도 없음: 노랑선이 D4 단자에 있는지, 4.7kΩ 저항(D4~3.3V)이 있는지, 빨강·검정이 바뀌지 않았는지 확인");
  }
  sensors.setWaitForConversion(true);
  for (int i = 0; i < nSensors; i++) {
    sensors.getAddress(addr[i], i);
    sensors.setResolution(addr[i], 12);
  }
  sensors.requestTemperatures();
  for (int i = 0; i < nSensors; i++) {
    float c = sensors.getTempC(addr[i]);
    Serial.printf("  %d번 센서: %.2f C   주소 = ", i, c);
    printAddr(addr[i]);
    Serial.println();
  }
  if (nSensors == 1) Serial.println("  -> 1개만 잡힘: 두 번째 센서 노랑선이 D4 단자에 들어갔는지 확인");
  if (nSensors >= 1) Serial.println("  센서 하나를 손으로 쥐고 실시간 보기에서 어느 번호가 오르는지 보세요.");
  sensors.setWaitForConversion(false);
  sensors.requestTemperatures();
}

// ---------------- 2. LED ----------------
void checkLed() {
  Serial.println("\n[2] 신호등 LED 점검 (각 1초)");
  const int pins[3] = {PIN_LED_R, PIN_LED_Y, PIN_LED_G};
  const char* names[3] = {"RED (D26)", "YELLOW (D33)", "GREEN (D32)"};
  for (int i = 0; i < 3; i++) {
    digitalWrite(pins[i], HIGH);
    Serial.printf("  %s 켬\n", names[i]);
    oledMsg(i == 0 ? "LED RED" : i == 1 ? "LED YEL" : "LED GRN", names[i]);
    delay(1000);
    digitalWrite(pins[i], LOW);
  }
  Serial.println("  -> 이름과 다른 색이 켜지면 선이 바뀐 것. 켜야 할 때 꺼지고 나머지가 켜지면 반대 방식 모듈.");
}

// ---------------- 3. 부저 ----------------
void checkBuzzer() {
  Serial.println("\n[3] 부저 점검");
  pinMode(PIN_BUZ, OUTPUT);
  Serial.println("  지금 D25 = LOW  (1초)");
  oledMsg("BUZ LOW", "sound now? -> LOW type");
  digitalWrite(PIN_BUZ, LOW);  delay(1000);
  Serial.println("  지금 D25 = HIGH (1초)");
  oledMsg("BUZ HIGH", "sound now? -> HIGH type");
  digitalWrite(PIN_BUZ, HIGH); delay(1000);
  buzzerQuiet();
  Serial.println("  -> LOW일 때 울렸으면 precool_ai의 BUZZER_ON_LEVEL = LOW (지금 설정 그대로)");
  Serial.println("  -> HIGH일 때 울렸으면 BUZZER_ON_LEVEL = HIGH 로 바꾸기");
}

// ---------------- 4. 서보 ----------------
void checkServo() {
  Serial.println("\n[4] 서보 댐퍼 점검");
  int seq[3] = {0, 90, 0};
  for (int i = 0; i < 3; i++) {
    damper.write(seq[i]);
    Serial.printf("  %d도\n", seq[i]);
    char b[12]; snprintf(b, sizeof(b), "SERVO %d", seq[i]);
    oledMsg(b, "D18, 5V");
    delay(1200);
  }
  Serial.println("  -> 안 움직이면 주황선 D18, 빨강 5V 묶음, 갈색 GND 확인");
}

// ---------------- 5. 팬 ----------------
void checkFan() {
  Serial.println("\n[5] 팬 점검 (3초 켬)");
  oledMsg("FAN ON", "D19 HIGH");
  digitalWrite(PIN_FAN, HIGH);
  delay(3000);
  digitalWrite(PIN_FAN, LOW);
  oledMsg("FAN OFF", "D19 LOW");
  Serial.println("  -> 안 돌면 MOSFET 모듈 + / LOAD / - 연결과 팬 선 방향 확인");
  Serial.println("  -> 약하게 돌면 5V 전원으로는 모듈이 충분히 못 켜는 것일 수 있음");
  delay(500);
}

// ---------------- 6. 리드스위치 ----------------
void checkReed() {
  Serial.println("\n[6] 리드스위치 점검 (8초 동안 자석을 댔다 뗐다 해 보세요)");
  int last = -1;
  unsigned long t0 = millis();
  while (millis() - t0 < 8000) {
    int v = digitalRead(PIN_REED);
    if (v != last) {
      Serial.printf("  D27 = %s\n", v == LOW ? "LOW" : "HIGH");
      oledMsg(v == LOW ? "D27 LOW" : "D27 HIGH", "magnet on / off");
      last = v;
    }
    delay(20);
  }
  Serial.println("  -> 자석 댔을 때 LOW면 precool_ai의 LID_CLOSED_LEVEL = LOW (지금 설정 그대로)");
  Serial.println("  -> 자석 댔을 때 HIGH면 LID_CLOSED_LEVEL = HIGH 로 바꾸기");
  Serial.println("  -> 값이 전혀 안 바뀌면 DO선 D27, 모듈 VCC 3.3V, GND 확인 / 자석을 더 가까이");
}

void checkAll() {
  checkTemp(); checkLed(); checkBuzzer(); checkServo(); checkFan(); checkReed();
  Serial.println("\n=== 전체 점검 끝. 이제 실시간 보기 (숫자 1~6, 0으로 다시 점검) ===");
}

void liveView() {
  static unsigned long last = 0;
  if (millis() - last < 2000) return;
  last = millis();
  float c[4];
  for (int i = 0; i < nSensors; i++) c[i] = sensors.getTempC(addr[i]);
  sensors.requestTemperatures();   // 다음 2초 뒤에 읽을 값 준비
  int v = digitalRead(PIN_REED);

  Serial.print("[실시간] ");
  for (int i = 0; i < nSensors; i++) Serial.printf("%d번=%.2fC  ", i, c[i]);
  if (nSensors == 0) Serial.print("센서없음  ");
  Serial.printf("D27=%s\n", v == LOW ? "LOW" : "HIGH");

  if (!oledOk) return;
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);
  oled.setTextSize(1);
  oled.setCursor(0, 0);
  oled.printf("LIVE  sensors:%d", nSensors);
  for (int i = 0; i < 2; i++) {
    oled.setCursor(0, 14 + i * 16);
    oled.setTextSize(2);
    if (i < nSensors && c[i] > -55) oled.printf("%d %.1fC", i, c[i]);
    else oled.printf("%d --", i);
  }
  oled.setTextSize(1);
  oled.setCursor(0, 52);
  oled.printf("D27=%s  send 0~6", v == LOW ? "LOW" : "HIGH");
  oled.display();
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n===== 부품 점검 시작 =====");

  pinMode(PIN_FAN, OUTPUT); digitalWrite(PIN_FAN, LOW);
  pinMode(PIN_LED_R, OUTPUT); pinMode(PIN_LED_Y, OUTPUT); pinMode(PIN_LED_G, OUTPUT);
  digitalWrite(PIN_LED_R, LOW); digitalWrite(PIN_LED_Y, LOW); digitalWrite(PIN_LED_G, LOW);
  buzzerQuiet();
  pinMode(PIN_REED, INPUT_PULLUP);

  damper.setPeriodHertz(50);
  damper.attach(PIN_SERVO, 500, 2400);
  damper.write(0);

  Wire.begin(21, 22);
  oledOk = oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  Serial.println(oledOk ? "[OLED] 찾음 (0x3C)" : "[OLED] 못 찾음: SDA D21, SCL D22, VCC 3.3V 확인 / 주소 0x3D일 수도 있음");

  checkAll();
}

void loop() {
  if (Serial.available()) {
    char ch = Serial.read();
    switch (ch) {
      case '1': checkTemp(); break;
      case '2': checkLed(); break;
      case '3': checkBuzzer(); break;
      case '4': checkServo(); break;
      case '5': checkFan(); break;
      case '6': checkReed(); break;
      case '0': checkAll(); break;
      default: break;   // 엔터 등은 무시
    }
  }
  liveView();
}
