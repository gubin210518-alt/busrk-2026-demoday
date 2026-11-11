/*
  반복 동작 시험 코드 (실내용)  -  TEAM 부스럭
  ==============================================
  출력 부품은 계속 껐다 켰다 하고, OLED에는 센서 상태를 보여 줘요.

  출력 (계속 반복)
    LED   : 1초마다 빨강 -> 노랑 -> 초록 -> 빨강 ... 차례로
    서보  : 2초마다 0도 <-> 90도
    팬    : 3초 켜기, 3초 끄기
    부저  : 2초마다 짧게 "삑"  (시끄러우면 BUZZER_ENABLE = false)

  OLED 표시
    맨 위 줄 : 찾은 온도센서 개수, 뚜껑(리드스위치) 상태
    가운데   : 온도센서 0번, 1번 온도 (OK = 정상, ERR = 못 읽음)
    맨 아래  : 지금 팬, 서보, LED 상태

  시리얼 모니터(115200)에도 2초마다 같은 내용을 찍어요.
  필요한 라이브러리: OneWire, DallasTemperature, ESP32Servo,
                    Adafruit SSD1306, Adafruit GFX Library
*/

#include <OneWire.h>
#include <DallasTemperature.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// ---------------- 바꿔도 되는 설정 ----------------
const bool BUZZER_ENABLE = true;     // 부저 소리 끄려면 false
const int  BUZZER_ON_LEVEL = LOW;    // LOW일 때 울리는 모듈 기준. HIGH일 때 울리면 HIGH로
const int  LID_CLOSED_LEVEL = LOW;   // 자석을 댔을 때(닫힘) D27 값. 반대면 HIGH로

const unsigned long LED_STEP_MS = 1000;   // LED 바뀌는 간격
const unsigned long SERVO_MS    = 2000;   // 서보 움직이는 간격
const unsigned long FAN_MS      = 3000;   // 팬 켜기/끄기 시간
const unsigned long BEEP_EVERY  = 2000;   // 부저 울리는 간격
const unsigned long BEEP_LEN    = 100;    // 부저 한 번 길이

// ---------------- 핀 ----------------
const int PIN_DS = 4, PIN_SERVO = 18, PIN_FAN = 19;
const int PIN_REED = 27, PIN_LED_R = 26, PIN_LED_Y = 33, PIN_LED_G = 32, PIN_BUZ = 25;

OneWire oneWire(PIN_DS);
DallasTemperature sensors(&oneWire);
Servo damper;
Adafruit_SSD1306 oled(128, 64, &Wire, -1);
bool oledOk = false;

int nSensors = 0;
DeviceAddress addr[2];
float tempC[2] = {NAN, NAN};

// 지금 출력 상태 (화면에 보여 주려고 기억)
int ledIdx = 0;            // 0 빨강, 1 노랑, 2 초록
int servoDeg = 0;
bool fanOn = false;
bool lidClosed = false;
int reedRaw = HIGH;

bool validTemp(float c) {
  return !isnan(c) && c != DEVICE_DISCONNECTED_C && c > -55 && c < 125;
}

void buzzer(bool on) {
  digitalWrite(PIN_BUZ, on ? BUZZER_ON_LEVEL : !BUZZER_ON_LEVEL);
}

void setLed(int idx) {
  digitalWrite(PIN_LED_R, idx == 0);
  digitalWrite(PIN_LED_Y, idx == 1);
  digitalWrite(PIN_LED_G, idx == 2);
}

void findSensors() {
  sensors.begin();
  nSensors = min((int)sensors.getDeviceCount(), 2);
  for (int i = 0; i < nSensors; i++) {
    sensors.getAddress(addr[i], i);
    sensors.setResolution(addr[i], 12);
  }
  sensors.setWaitForConversion(false);   // 기다리지 않고 다른 동작 계속
  sensors.requestTemperatures();
  Serial.printf("온도센서 %d개 찾음\n", nSensors);
}

void drawOled() {
  if (!oledOk) return;
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);

  // 맨 위 줄 (노란 영역)
  oled.setTextSize(1);
  oled.setCursor(0, 0);
  oled.printf("TEMP x%d  LID:%s", nSensors, lidClosed ? "CLOSE" : "OPEN");

  // 온도센서 2개
  for (int i = 0; i < 2; i++) {
    int y = 16 + i * 17;
    oled.setTextSize(2);
    oled.setCursor(0, y);
    if (i < nSensors && validTemp(tempC[i])) oled.printf("%d %.1fC", i, tempC[i]);
    else oled.printf("%d --.-C", i);
    oled.setTextSize(1);
    oled.setCursor(104, y + 4);
    if (i >= nSensors) oled.print("NONE");
    else oled.print(validTemp(tempC[i]) ? "OK" : "ERR");
  }

  // 맨 아래 줄: 출력 상태
  const char* ledName[3] = {"R", "Y", "G"};
  oled.setTextSize(1);
  oled.setCursor(0, 56);
  oled.printf("FAN %s SV%d LED %s", fanOn ? "ON " : "OFF", servoDeg, ledName[ledIdx]);
  oled.display();
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n===== 반복 동작 시험 시작 =====");

  pinMode(PIN_FAN, OUTPUT); digitalWrite(PIN_FAN, LOW);
  pinMode(PIN_LED_R, OUTPUT); pinMode(PIN_LED_Y, OUTPUT); pinMode(PIN_LED_G, OUTPUT);
  setLed(0);
  pinMode(PIN_BUZ, OUTPUT); buzzer(false);
  pinMode(PIN_REED, INPUT_PULLUP);

  damper.setPeriodHertz(50);
  damper.attach(PIN_SERVO, 500, 2400);
  damper.write(0);

  Wire.begin(21, 22);
  oledOk = oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  Serial.println(oledOk ? "OLED 찾음" : "OLED 못 찾음 (SDA D21, SCL D22 확인, 주소 0x3D일 수도)");

  findSensors();
}

void loop() {
  static unsigned long tLed = 0, tServo = 0, tFan = 0, tBeep = 0, tTemp = 0, tDraw = 0, tPrint = 0;
  unsigned long now = millis();

  // LED: 1초마다 다음 색
  if (now - tLed >= LED_STEP_MS) {
    tLed = now;
    ledIdx = (ledIdx + 1) % 3;
    setLed(ledIdx);
  }

  // 서보: 2초마다 0도 <-> 90도
  if (now - tServo >= SERVO_MS) {
    tServo = now;
    servoDeg = (servoDeg == 0) ? 90 : 0;
    damper.write(servoDeg);
  }

  // 팬: 3초마다 켜기/끄기
  if (now - tFan >= FAN_MS) {
    tFan = now;
    fanOn = !fanOn;
    digitalWrite(PIN_FAN, fanOn ? HIGH : LOW);
  }

  // 부저: 2초마다 짧게
  if (BUZZER_ENABLE) {
    if (now - tBeep >= BEEP_EVERY) tBeep = now;
    buzzer(now - tBeep < BEEP_LEN);
  } else {
    buzzer(false);
  }

  // 리드스위치
  reedRaw = digitalRead(PIN_REED);
  lidClosed = (reedRaw == LID_CLOSED_LEVEL);

  // 온도: 1초마다 읽고 다음 측정 시작 (12비트 변환은 최대 0.75초)
  if (now - tTemp >= 1000) {
    tTemp = now;
    for (int i = 0; i < nSensors; i++) tempC[i] = sensors.getTempC(addr[i]);
    sensors.requestTemperatures();
    if (nSensors < 2 && (now / 1000) % 10 == 0) findSensors();   // 2개가 안 잡히면 10초마다 다시 찾기
  }

  // 화면 0.3초마다
  if (now - tDraw >= 300) { tDraw = now; drawOled(); }

  // 시리얼 2초마다
  if (now - tPrint >= 2000) {
    tPrint = now;
    Serial.printf("센서 %d개 | ", nSensors);
    for (int i = 0; i < 2; i++) {
      if (i < nSensors && validTemp(tempC[i])) Serial.printf("%d번 %.2fC OK | ", i, tempC[i]);
      else Serial.printf("%d번 %s | ", i, i < nSensors ? "ERR" : "없음");
    }
    Serial.printf("D27=%s(%s) | 팬 %s | 서보 %d도 | LED %c\n",
                  reedRaw == LOW ? "LOW" : "HIGH", lidClosed ? "닫힘" : "열림",
                  fanOn ? "ON" : "OFF", servoDeg, "RYG"[ledIdx]);
  }
}
