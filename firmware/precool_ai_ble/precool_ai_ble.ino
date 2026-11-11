/*
  예냉박스 AI 펌웨어 (블루투스 BLE 판)  -  TEAM 부스럭 (신정고 매니악코더)
  (학습 모델(AI) 예측 + PC 프로그램(AI 시뮬레이터)용 데이터 출력 + 안드로이드 앱 원격 확인)
  ==============================================================
  precool_ai.ino에서 달라진 점
    1) 블루투스(BLE)로 안드로이드 앱 "부스럭 예냉박스"에 상태를 2초마다 보내요.
    2) 뚜껑을 열었다 닫은 직후 AI 예측이 크게 튀던 문제를 고쳤어요.
       최근 2분 안에 뚜껑이 열렸거나 팬이 꺼졌으면, 마지막으로 깨끗했던 AI 예측을 유지하고
       지난 시간만큼만 줄여요. OLED에는 "AI~"로 표시돼요.
    3) 앱에서 "새 딸기 시작"을 하려면 6자리 PIN이 필요해요 (BLE_PIN). 000000이면 원격 명령이 꺼져요.
  업로드 전 꼭 확인
    - Arduino IDE 메뉴 [도구] → [Partition Scheme] → "Huge APP (3MB No OTA/1MB SPIFFS)"
      (블루투스 코드가 커서 기본 설정이면 "Sketch too big" 오류가 날 수 있어요)
    - BLE_PIN을 나만 아는 6자리 숫자로 바꾸기
  ==============================================================
  수확한 딸기를 넣고 전원을 켜면, 바로 찬 공기를 돌려 식히기 시작해요.
  "수확 후 1시간 안에 목표 온도까지 식힐 수 있을지"를 미리 계산해서 알려줘요.

  인지 -> 판단 -> 행동
    인지: DS18B20 2개 (식품칸 공기, 냉기실 공기), 리드스위치(뚜껑)
    판단: 목표 온도(1℃)까지 남은 시간 예측, 1시간 안에 될지 판단, 경과 시간 기록
    행동: 넣자마자 댐퍼 열고 팬 켜기 / OLED, 신호등 LED, 부저로 알림 / 기록 저장

  예측 방법 (두 가지를 같이 계산해요)
    1) 뉴턴 냉각 법칙: 식는 속도는 "식품칸 온도 - 냉기 온도" 차이에 비례해요.
       T(t) = 냉기온도 + (처음 온도 - 냉기온도) x e^(-k t)
       최근 10분 기록으로 k를 구하고, 목표 온도까지 남은 시간을 계산해요.
       냉기실 센서를 못 읽으면 직선 추세로 대신 계산해요.
    2) 학습 모델(AI): 실험 기록에서 "지금 온도와 최근 2분 기울기 -> 2분 뒤 온도 변화"를
       배운 회귀 모델(model.h)이에요. 이걸 2분씩 반복해서 목표까지 남은 시간을 계산해요.
       모델 숫자는 PC 프로그램(AI 시뮬레이터 https://busrk-ai-studio.vercel.app)에서 학습해서 만들어요. (이 파일 안 "학습 모델" 블록)
       처음 들어 있는 model.h는 시뮬레이션으로 미리 학습한 것이라, 실제 기록으로 다시 학습해야 해요.
    상태 판단에는 AI 예측을 쓰고, AI가 아직 계산을 못 하면 뉴턴 예측을 써요.

  기준 (출처: Ontario 농식품부 딸기 냉각 안내, Strawberry Center 블로그)
    - 수확 후 1시간 안에 예냉을 하는 게 중요하다.
    - 딸기는 가능한 0℃ 가까이 유지한다. (과육 목표 34°F, 약 1℃)
    - 이 박스(아이스팩)로 1℃까지 내려가는지는 아직 실험 전이에요.

  핀 (보드에 인쇄된 이름 = 코드 숫자)
    D4  DS18B20 2개 DATA (4.7kΩ로 3.3V에 풀업, 1개만)
    D18 서보 신호        D19 MOSFET 모듈 PWM
    D21 OLED SDA         D22 OLED SCL
    D27 리드스위치 DO    D26/D33/D32 LED 빨강/노랑/초록
    D25 부저 I/O

  필요한 라이브러리: OneWire, DallasTemperature, ESP32Servo,
                    Adafruit SSD1306, Adafruit GFX Library
  보드: "ESP32 Dev Module"

  시리얼 모니터(115200) 명령
    r : 새 딸기를 넣었을 때 시간 다시 시작 (수확 후 시간 0분부터)
    a : 연결된 온도센서 주소 다시 찍기
    d : 저장된 기록(log.csv) 전부 출력
    x : 저장된 기록 지우기
    m : 들어 있는 학습 모델 정보 출력

  PC 프로그램용 데이터 줄 (기록 간격마다, 보통 10초):
    D,분,식품칸,냉기실,뚜껑열림,팬,상태,뉴턴ETA,AI_ETA   (ETA: -1 모름, -2 못 닿음)
*/

#include <OneWire.h>
#include <DallasTemperature.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <LittleFS.h>
#include <math.h>
#include <string.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
// ===================== 학습 모델 (model.h) =====================
// PC 프로그램에서 새 모델을 만들면 이 블록(MODEL_READY ~ MODEL_WC)을 내려받은 model.h 내용으로 바꿔요.
// model.h — 부스럭 예냉박스 학습 모델 (AI 스튜디오에서 자동 생성)
// 만든 시각: 2026-10-30 14:37 UTC
// 학습 데이터: 실험 60개, 학습 표본 31020개 (simulation pretrain - retrain with real logs)
// 2분 뒤 온도 변화 학습 오차: 식품칸 0.064℃, 냉기실 0.055℃
// 특징 순서: [식품칸-냉기실, 냉기실, 식품칸 2분 기울기(℃/분), 냉기실 2분 기울기(℃/분)]
#define MODEL_READY 1
#define MODEL_H_MIN 2.0f
static const char MODEL_INFO[] = "runs=60 n=31020 simulation pretrain - retrain with real logs";
static const float MODEL_MEAN[4] = { 4.6072292f, -1.0956218f, -0.23671321f, -0.054210993f };
static const float MODEL_STD[4]  = { 3.3321275f, 2.8593341f, 0.47531228f, 0.14640631f };
static const float MODEL_WB[5]   = { -0.34824508f, -0.074021964f, -0.0074842680f, 0.36901835f, 0.099496979f };  // 식품칸 2분 뒤 변화: 절편 + 가중치 4개
static const float MODEL_WC[5]   = { -0.10110413f, 0.00061909434f, -0.010955172f, 0.084260069f, 0.14382618f };  // 냉기실 2분 뒤 변화
// ===================== AI 계산 (ai_predict.h) =====================
// ai_predict.h — 학습 모델로 목표 온도까지 남은 시간을 계산 (펌웨어·PC 프로그램과 같은 계산)
#include <math.h>

// 2분 뒤 변화 예측
static inline void aiStep(float b, float c, float sb, float sc, float& db, float& dc) {
  const float x[4] = { b - c, c, sb, sc };
  db = MODEL_WB[0]; dc = MODEL_WC[0];
  for (int k = 0; k < 4; k++) {
    float z = (x[k] - MODEL_MEAN[k]) / MODEL_STD[k];
    db += MODEL_WB[k + 1] * z; dc += MODEL_WC[k + 1] * z;
  }
}

// 반환: 남은 분 (0 = 이미 도달), -2 = 180분 안에 못 닿음
static inline float aiEta(float b, float c, float sb, float sc, float target) {
  if (b <= target) return 0;
  float t = 0;
  for (int s = 0; s < 90; s++) {
    float db, dc; aiStep(b, c, sb, sc, db, dc);
    float nb = b + db, nc = c + dc;
    if (nb <= target) return t + MODEL_H_MIN * (b - target) / (b - nb);
    if (db > -0.005f) return -2;
    b = nb; c = nc; sb = db / MODEL_H_MIN; sc = dc / MODEL_H_MIN; t += MODEL_H_MIN;
  }
  return -2;
}
// ================================================================

// ---------------- 바꿔도 되는 설정 ----------------
// 시연 모드: 영상·발표에서 짧은 시간 안에 전체 동작을 보여 주기 위한 설정이에요.
// true로 하면 목표 온도와 제한 시간이 아래 DEMO 값으로 바뀌고, OLED에 "DEMO"가 표시돼요.
// 실제 사용과 실험 기록을 모을 때는 반드시 false로 해요.
const bool  DEMO_MODE          = false;
const float DEMO_TARGET_C      = 15.0;  // 시연용 목표 온도. 시작할 때 식품칸 온도보다 5~8℃ 낮게 잡아요
const float DEMO_DEADLINE_MIN  = 10.0;  // 시연용 제한 시간 (분)

const float TARGET_C       = DEMO_MODE ? DEMO_TARGET_C : 1.0;     // 딸기 목표 온도 (34°F 약 1℃)
const float RESTART_C      = TARGET_C + 0.5;  // 다 식은 뒤 이 온도를 넘으면 다시 식히기 시작
const float FREEZE_GUARD_C = 0.0;   // 식품칸 공기가 이보다 차가우면 팬을 멈춤 (얼지 않게)
const float DEADLINE_MIN   = DEMO_MODE ? DEMO_DEADLINE_MIN : 60.0; // 수확 후 이 시간(분) 안에 식히는 게 목표
const unsigned long HIST_MS = DEMO_MODE ? 5000 : 10000;  // 예측용 기록 간격 (시연 땐 더 자주)
const unsigned long LID_ALARM_MS = 30000;  // 뚜껑이 30초 넘게 열려 있으면 부저
const bool  USE_AI = true;          // false면 뉴턴 예측만 써요

// 블루투스(BLE) 원격 확인
// 앱에서 "새 딸기 시작"을 누를 때 넣는 6자리 숫자예요. 꼭 바꿔요. "000000"이면 원격 명령을 받지 않아요(보기만 가능).
const char BLE_PIN[] = "000000";
const unsigned long BLE_STATUS_MS = 2000;   // 앱으로 상태를 보내는 간격

const int DAMPER_CLOSED_DEG = 0;    // 댐퍼 닫힘 각도 (실제 상자에 맞춰 조정)
const int DAMPER_OPEN_DEG   = 90;   // 댐퍼 열림 각도

// 리드스위치: 자석이 가까이(뚜껑 닫힘) 있을 때 DO가 LOW인 모듈 기준. 반대면 HIGH.
const int LID_CLOSED_LEVEL = LOW;
// 부저: LOW일 때 울리는 모듈 기준. 반대면 HIGH.
const int BUZZER_ON_LEVEL = LOW;

// 온도센서 주소. 비워 두면(전부 0) 찾은 순서대로 0번=식품칸, 1번=냉기실.
DeviceAddress ADDR_BERRY = {0, 0, 0, 0, 0, 0, 0, 0};
DeviceAddress ADDR_COLD  = {0, 0, 0, 0, 0, 0, 0, 0};

// ---------------- 핀 ----------------
const int PIN_DS = 4, PIN_SERVO = 18, PIN_FAN = 19;
const int PIN_REED = 27, PIN_LED_R = 26, PIN_LED_Y = 33, PIN_LED_G = 32, PIN_BUZ = 25;

// ---------------- 부품 객체 ----------------
OneWire oneWire(PIN_DS);
DallasTemperature sensors(&oneWire);
Servo damper;
Adafruit_SSD1306 oled(128, 64, &Wire, -1);
bool oledOk = false, fsOk = false;

// ---------------- 상태 ----------------
// COOLING 식히는 중(1시간 안에 될 것 같음) / DONE 목표 도달
// LATE_RISK 이대로면 1시간 안에 못 식힘 / LATE 1시간이 지났는데 아직 못 식힘
enum Level { COOLING, DONE, LATE_RISK, LATE, SENSOR_ERR };
float berryC = NAN, coldC = NAN;
bool lidOpen = false, cooling = false, doneLatched = false;
unsigned long lidOpenSince = 0, startMs = 0;
float doneAtMin = -1;          // 목표에 처음 도달한 시각(분)
Level level = COOLING;

// 예측 결과
float etaMin = -1;             // 목표까지 남은 예상 시간(분), -1 = 아직 모름
bool unreachable = false;      // 냉기가 목표보다 따뜻해서 목표까지 못 내려감
float kPerMin = 0;             // 뉴턴 냉각 상수 k (1/분)
char method = '-';             // 'N' 뉴턴 냉각, 'L' 직선 추세
float etaAiMin = -1;           // AI 예측 남은 시간(분), -1 = 아직 모름, -2 = 180분 안에 못 닿음
float etaUsed = -1;            // 상태 판단에 실제로 쓴 값
char usedBy = '-';             // 'A' AI, 'N' 뉴턴, 'L' 직선

// 10초마다 저장, 최근 60개(10분)
const int N_HIST = 60;
float histT[N_HIST], histB[N_HIST], histC[N_HIST];
bool histOk[N_HIST];           // 그 순간 팬이 켜져 있고 뚜껑이 닫혀 있었으면 true (AI 기울기에 쓸 수 있음)
int histCount = 0, histHead = 0;

// 뚜껑·팬 보정용: 마지막으로 깨끗한 구간에서 계산한 AI 예측
bool  aiHeldValid = false;
float aiHeldEta = -1;          // 그때 계산한 남은 시간(분) 또는 -2
float aiHeldAt = 0;            // 그때 시각(분)
bool  aiHold = false;          // 지금 etaAiMin이 유지값이면 true

// ---------------- 블루투스(BLE) ----------------
bool validTemp(float c);   // 아래에서 만드는 함수 (먼저 알려 둠)
void resetRun();
// 안드로이드 앱과 약속한 번호(UUID). 앱 코드(BoxProtocol.kt)와 똑같아야 해요.
#define BOX_SERVICE_UUID "b5a10001-7c3e-4f2a-9d6b-1e8c0f4a2b70"
#define BOX_STATUS_UUID  "b5a10002-7c3e-4f2a-9d6b-1e8c0f4a2b70"   // 읽기·알림: 상태 20바이트
#define BOX_CONFIG_UUID  "b5a10003-7c3e-4f2a-9d6b-1e8c0f4a2b70"   // 읽기: 설정 10바이트
#define BOX_CMD_UUID     "b5a10004-7c3e-4f2a-9d6b-1e8c0f4a2b70"   // 쓰기: 명령, 알림: 결과 글자
#define BOX_HIST_UUID    "b5a10005-7c3e-4f2a-9d6b-1e8c0f4a2b70"   // 알림: 최근 10분 기록 14바이트씩
const uint8_t BLE_PROTO_VERSION = 1;

BLECharacteristic *chStatus = nullptr, *chConfig = nullptr, *chCmd = nullptr, *chHist = nullptr;
volatile bool bleConnected = false;
volatile bool bleCmdPending = false;
char bleCmdBuf[16];
int  pinFails = 0;
unsigned long pinLockUntil = 0;
uint16_t statusSeq = 0;
int histSendLeft = 0, histSendTotal = 0;   // 기록 전송 중이면 남은 개수
unsigned long lastHistSend = 0;

bool blePinSet() { return strcmp(BLE_PIN, "000000") != 0 && strlen(BLE_PIN) == 6; }

class BoxServerCb : public BLEServerCallbacks {
  void onConnect(BLEServer* s) override { bleConnected = true; }
  void onDisconnect(BLEServer* s) override {
    bleConnected = false;
    BLEDevice::startAdvertising();       // 끊기면 다시 찾을 수 있게 광고 재시작
  }
};

// 명령은 블루투스 작업 안에서 받아 두기만 하고, 실제 처리는 loop()에서 해요 (동시에 값이 바뀌지 않게).
class BoxCmdCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    if (bleCmdPending) return;
    auto v = c->getValue();
    size_t n = v.length();
    if (n == 0 || n >= sizeof(bleCmdBuf)) return;
    for (size_t i = 0; i < n; i++) bleCmdBuf[i] = v[i];
    bleCmdBuf[n] = 0;
    bleCmdPending = true;
  }
};

static void put16(uint8_t* p, int16_t v) { p[0] = v & 0xFF; p[1] = (v >> 8) & 0xFF; }
static void putU16(uint8_t* p, uint16_t v) { p[0] = v & 0xFF; p[1] = (v >> 8) & 0xFF; }
static void putU32(uint8_t* p, uint32_t v) { for (int i = 0; i < 4; i++) p[i] = (v >> (8 * i)) & 0xFF; }
// 온도(℃)를 0.01℃ 단위 정수로. 못 읽으면 -32768
static int16_t enc100(float c) { return validTemp(c) ? (int16_t)lroundf(c * 100) : (int16_t)-32768; }
// 분을 0.1분 단위 정수로 (-1 모름 → -10, -2 못 닿음 → -20)
static int16_t enc10(float m) { if (m > 3000) m = 3000; return (int16_t)lroundf(m * 10); }

void bleSendConfig() {
  uint8_t p[10];
  put16(p, (int16_t)lroundf(TARGET_C * 100));
  putU16(p + 2, (uint16_t)lroundf(DEADLINE_MIN * 10));
  p[4] = DEMO_MODE ? 1 : 0;
  p[5] = strstr(MODEL_INFO, "simulation") ? 1 : 0;     // 1 = 시뮬레이션으로 학습한 모델
  p[6] = blePinSet() ? 1 : 0;                          // 1 = 원격 명령 가능
  p[7] = BLE_PROTO_VERSION;
  putU16(p + 8, (uint16_t)(HIST_MS / 1000));
  chConfig->setValue(p, sizeof(p));
}

// 상태 20바이트 (작은 끝 순서). 앱 BoxProtocol.parseStatus()와 같은 순서예요.
void bleSendStatus() {
  if (!chStatus) return;
  uint8_t p[20];
  float ePrint = unreachable ? -2.0f : etaMin;
  putU16(p, statusSeq++);
  putU32(p + 2, (millis() - startMs) / 1000);
  put16(p + 6, enc100(berryC));
  put16(p + 8, enc100(coldC));
  p[10] = (uint8_t)level;
  p[11] = (lidOpen ? 1 : 0) | (cooling ? 2 : 0) | (DEMO_MODE ? 4 : 0) | (aiHold ? 8 : 0) |
          (doneLatched ? 16 : 0) | (lidOpen && millis() - lidOpenSince > LID_ALARM_MS ? 32 : 0);
  put16(p + 12, enc10(etaUsed));
  p[14] = (uint8_t)usedBy;
  p[15] = doneAtMin < 0 ? 255 : (uint8_t)min(254, (int)(doneAtMin + 0.5));
  put16(p + 16, enc10(ePrint));
  put16(p + 18, enc10(etaAiMin));
  chStatus->setValue(p, sizeof(p));
  if (bleConnected) chStatus->notify();
}

void bleReply(const char* s) {
  if (!chCmd) return;
  chCmd->setValue((uint8_t*)s, strlen(s));
  if (bleConnected) chCmd->notify();
}

void bleHandleCmd() {
  if (!bleCmdPending) return;
  char c = bleCmdBuf[0];
  const char* arg = bleCmdBuf + 1;
  if (c == 'h') {                                          // 최근 10분 기록 보내기 (보기 전용이라 PIN 필요 없음)
    histSendTotal = histCount; histSendLeft = histCount;
    bleReply(histCount ? "OK h" : "EMPTY");
  } else if (c == 'r') {                                   // 새 딸기 시작 (PIN 필요)
    if (!blePinSet()) bleReply("NOPIN");
    else if ((long)(pinLockUntil - millis()) > 0) bleReply("LOCK");
    else if (strcmp(arg, BLE_PIN) == 0) { pinFails = 0; resetRun(); bleReply("OK r"); bleSendStatus(); }
    else {
      if (++pinFails >= 5) { pinFails = 0; pinLockUntil = millis() + 60000; bleReply("LOCK"); }   // 5번 틀리면 1분 잠금
      else bleReply("PIN");
    }
  } else bleReply("?");
  bleCmdPending = false;
}

// 기록 한 칸 14바이트: 번호, 전체 개수, 시각(ms), 식품칸, 냉기실, 깨끗함 여부, 예비
void bleSendHistStep() {
  if (histSendLeft <= 0 || !bleConnected) { histSendLeft = 0; return; }
  if (millis() - lastHistSend < 15) return;               // 너무 빨리 보내면 일부가 사라져요
  lastHistSend = millis();
  if (histSendTotal > histCount) { histSendLeft = 0; return; }   // 그 사이 새 딸기로 초기화됨
  int k = histSendTotal - histSendLeft;                    // 0 = 가장 오래된 기록
  int oldest = (histHead - histCount + N_HIST) % N_HIST;
  int i = (oldest + k) % N_HIST;
  uint8_t p[14];
  putU16(p, k); putU16(p + 2, histSendTotal);
  putU32(p + 4, (uint32_t)lroundf(histT[i] * 60000.0f));
  put16(p + 8, enc100(histB[i]));
  put16(p + 10, isnan(histC[i]) ? (int16_t)-32768 : enc100(histC[i]));
  p[12] = histOk[i] ? 1 : 0; p[13] = 0;
  chHist->setValue(p, sizeof(p));
  chHist->notify();
  histSendLeft--;
}

void bleBegin() {
  uint64_t mac = ESP.getEfuseMac();
  char name[16];
  snprintf(name, sizeof(name), "BUSRK-%02X%02X", (unsigned)((mac >> 32) & 0xFF), (unsigned)((mac >> 40) & 0xFF));
  BLEDevice::init(name);
  BLEServer* srv = BLEDevice::createServer();
  srv->setCallbacks(new BoxServerCb());
  BLEService* svc = srv->createService(BOX_SERVICE_UUID);
  chStatus = svc->createCharacteristic(BOX_STATUS_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  chStatus->addDescriptor(new BLE2902());
  chConfig = svc->createCharacteristic(BOX_CONFIG_UUID, BLECharacteristic::PROPERTY_READ);
  chCmd = svc->createCharacteristic(BOX_CMD_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
  chCmd->addDescriptor(new BLE2902());
  chCmd->setCallbacks(new BoxCmdCb());
  chHist = svc->createCharacteristic(BOX_HIST_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  chHist->addDescriptor(new BLE2902());
  bleSendConfig();
  svc->start();
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(BOX_SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.printf("블루투스 이름: %s\n", name);
  if (!blePinSet()) Serial.println("BLE_PIN이 000000이라 앱에서는 보기만 할 수 있어요 (원격 명령 꺼짐)");
}

bool isEmpty(const DeviceAddress a) {
  for (int i = 0; i < 8; i++) if (a[i]) return false;
  return true;
}

void printAddress(const DeviceAddress a) {
  Serial.print("{");
  for (int i = 0; i < 8; i++) {
    Serial.printf("0x%02X", a[i]);
    if (i < 7) Serial.print(", ");
  }
  Serial.print("}");
}

void listSensors() {
  sensors.begin();
  int n = sensors.getDeviceCount();
  Serial.printf("온도센서 %d개 찾음\n", n);
  for (int i = 0; i < n; i++) {
    DeviceAddress a;
    if (sensors.getAddress(a, i)) {
      Serial.printf("  %d번 주소: ", i); printAddress(a); Serial.println();
      sensors.setResolution(a, 12);
    }
  }
  Serial.println("  센서를 하나씩 꽂아 주소를 확인한 뒤 ADDR_BERRY, ADDR_COLD에 넣어요.");
  if (isEmpty(ADDR_BERRY) && n >= 1) sensors.getAddress(ADDR_BERRY, 0);
  if (isEmpty(ADDR_COLD) && n >= 2) sensors.getAddress(ADDR_COLD, 1);
  sensors.setWaitForConversion(false);
}

bool validTemp(float c) {
  return !isnan(c) && c != DEVICE_DISCONNECTED_C && c > -55 && c < 125;
}

float elapsedMin() { return (millis() - startMs) / 60000.0; }

void resetRun() {
  startMs = millis();
  doneLatched = false; doneAtMin = -1;
  histCount = 0; histHead = 0;
  etaMin = -1; unreachable = false; kPerMin = 0; method = '-';
  etaAiMin = -1; etaUsed = -1; usedBy = '-';
  aiHeldValid = false; aiHeldEta = -1; aiHeldAt = 0; aiHold = false;
  Serial.println("새 딸기: 시간을 0분부터 다시 셉니다.");
}

// 최소제곱 직선의 기울기 (x 평균을 빼서 계산 오차를 줄임)
bool fitSlope(const float* x, const float* y, int n, float& slope) {
  if (n < 2) return false;
  float mx = 0, my = 0;
  for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
  mx /= n; my /= n;
  float sxx = 0, sxy = 0;
  for (int i = 0; i < n; i++) { float dx = x[i] - mx; sxx += dx * dx; sxy += dx * (y[i] - my); }
  if (sxx <= 0) return false;
  slope = sxy / sxx;
  return true;
}

// 목표 온도까지 남은 시간 예측
void updatePrediction() {
  etaMin = -1; unreachable = false; kPerMin = 0; method = '-';
  if (histCount < 12 || !validTemp(berryC)) return;         // 2분 이상 모여야 계산
  if (berryC <= TARGET_C) { etaMin = 0; return; }

  // 1) 뉴턴 냉각: ln(식품칸 - 냉기) 가 시간에 대해 직선이 돼요. 기울기 = -k
  float tc = 0; int nc = 0;
  for (int i = 0; i < histCount; i++) if (validTemp(histC[i])) { tc += histC[i]; nc++; }
  if (nc >= histCount * 0.8) {
    tc /= nc;                                               // 최근 10분 냉기 평균
    float xs[N_HIST], ys[N_HIST]; int m = 0;
    for (int i = 0; i < histCount; i++) {
      float d = histB[i] - tc;
      if (d > 0.3) { xs[m] = histT[i]; ys[m] = logf(d); m++; }
    }
    float b;
    if (m >= 12 && fitSlope(xs, ys, m, b) && -b > 0.001) {
      kPerMin = -b; method = 'N';
      if (TARGET_C <= tc + 0.2) { unreachable = true; return; }  // 냉기가 목표만큼 차갑지 않음
      etaMin = logf((berryC - tc) / (TARGET_C - tc)) / kPerMin;
      return;
    }
  }

  // 2) 냉기실 센서가 없을 때: 직선 추세
  float slope;
  if (fitSlope(histT, histB, histCount, slope) && slope < -0.01) {
    method = 'L';
    etaMin = (berryC - TARGET_C) / (-slope);
  }
}

// 기록에서 t분 시점의 값을 앞뒤 기록으로 보간 (없으면 NAN)
float histAt(const float* arr, float t) {
  if (histCount < 2) return NAN;
  int oldest = (histHead - histCount + N_HIST) % N_HIST;
  float pt = NAN, pv = NAN;
  for (int k = 0; k < histCount; k++) {
    int i = (oldest + k) % N_HIST;
    if (histT[i] >= t) {
      if (isnan(pt)) return (histT[i] - t < 0.05f) ? arr[i] : NAN;   // 기록이 t보다 늦게 시작
      if (isnan(arr[i]) || isnan(pv)) return NAN;
      float u = (t - pt) / (histT[i] - pt);
      return pv + (arr[i] - pv) * u;
    }
    pt = histT[i]; pv = arr[i];
  }
  return NAN;
}

// ==== AI-HOLD BEGIN (검증용 표시: 이 사이 코드는 PC 프로그램 AI.etaAIHold()와 같은 규칙) ====
// 최근 2분(+기록 한 칸) 동안 팬이 켜져 있고 뚜껑이 닫혀 있었는지
bool windowClean(float now) {
  float from = now - MODEL_H_MIN - 0.02f;   // 2분 전 기록(시간 오차 1초 허용)부터 지금까지
  for (int k = 0; k < histCount; k++) {
    int i = (histHead - 1 - k + N_HIST) % N_HIST;
    if (histT[i] < from) break;
    if (!histOk[i] || isnan(histB[i]) || isnan(histC[i])) return false;
  }
  return true;
}

// AI 예측: 최근 2분 기울기를 구해서 학습 모델로 계산
// 최근 2분 안에 뚜껑이 열렸거나 팬이 꺼졌으면 기울기가 틀어지므로, 마지막 깨끗한 예측을 유지하며 줄여요.
void updateAi() {
  etaAiMin = -1; aiHold = false;
  if (!USE_AI || !MODEL_READY || histCount < 2) return;
  int last = (histHead - 1 + N_HIST) % N_HIST;
  float b = histB[last], c = histC[last], now = histT[last];
  if (!isnan(b) && b <= TARGET_C) { etaAiMin = 0; return; }
  float b0 = histAt(histB, now - MODEL_H_MIN), c0 = histAt(histC, now - MODEL_H_MIN);
  bool fresh = !isnan(b) && !isnan(c) && !isnan(b0) && !isnan(c0) && windowClean(now);
  if (fresh) {
    etaAiMin = aiEta(b, c, (b - b0) / MODEL_H_MIN, (c - c0) / MODEL_H_MIN, TARGET_C);
    aiHeldValid = true; aiHeldEta = etaAiMin; aiHeldAt = now;
    return;
  }
  if (!aiHeldValid) return;                                  // 아직 깨끗한 예측이 없으면 뉴턴으로
  aiHold = true;
  if (aiHeldEta < 0) etaAiMin = aiHeldEta;                   // -2(못 닿음)는 그대로
  else { etaAiMin = aiHeldEta - (now - aiHeldAt); if (etaAiMin < 0) etaAiMin = 0; }
}
// ==== AI-HOLD END ====

void setLeds(bool r, bool y, bool g) {
  digitalWrite(PIN_LED_R, r); digitalWrite(PIN_LED_Y, y); digitalWrite(PIN_LED_G, g);
}

void buzzer(bool on) { digitalWrite(PIN_BUZ, on ? BUZZER_ON_LEVEL : !BUZZER_ON_LEVEL); }

void setCooling(bool on) {
  if (on == cooling) return;
  cooling = on;
  damper.write(on ? DAMPER_OPEN_DEG : DAMPER_CLOSED_DEG);
  digitalWrite(PIN_FAN, on ? HIGH : LOW);
}

void drawOled() {
  if (!oledOk) return;
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);
  oled.setTextSize(1);
  oled.setCursor(0, 0);
  switch (level) {   // OLED는 한글 글꼴이 없어서 영어로 표시해요.
    case COOLING:    oled.print("BERRY COOLING"); break;
    case DONE:       oled.printf("BERRY DONE %dm", (int)(doneAtMin + 0.5)); break;
    case LATE_RISK:  oled.printf("LATE RISK (>%dm)", (int)DEADLINE_MIN); break;
    case LATE:       oled.printf("LATE! OVER %dm", (int)DEADLINE_MIN); break;
    case SENSOR_ERR: oled.print("SENSOR?"); break;
  }
  oled.setTextSize(2);
  oled.setCursor(0, 10);
  if (validTemp(berryC)) oled.printf("%.1fC", berryC); else oled.print("--.-C");
  oled.setTextSize(1);
  oled.setCursor(0, 30);
  oled.print("AIR ");
  if (validTemp(coldC)) oled.printf("%.1fC", coldC); else oled.print("--");
  oled.print(cooling ? "  FAN ON" : "  FAN OFF");
  oled.setCursor(0, 40);
  unsigned long s = (millis() - startMs) / 1000;
  oled.printf("T+%lu:%02lu ", s / 60, s % 60);
  if (usedBy == '-') oled.print("ETA --");
  else if (etaUsed < 0) oled.print(usedBy == 'A' ? "ETA NO AI" : "ETA NO N");
  else oled.printf("ETA %dm %s", (int)(etaUsed + 0.5), usedBy == 'A' ? (aiHold ? "AI~" : "AI") : "N");
  oled.setCursor(0, 50);
  oled.print(lidOpen ? "LID OPEN" : "LID CLOSED");
  if (bleConnected) oled.print(" BT");
  if (DEMO_MODE) oled.printf(" DEMO%d/%dm", (int)TARGET_C, (int)DEADLINE_MIN);
  oled.display();
}

void appendLog() {
  if (!fsOk) return;
  File f = LittleFS.open("/log.csv", FILE_APPEND);
  if (!f) return;
  if (f.size() == 0) f.println("min,berry_c,cold_c,lid_open,cooling,level,k_per_min,eta_min,method,eta_ai");
  f.printf("%.2f,%.2f,%.2f,%d,%d,%d,%.4f,%.1f,%c,%.1f\n", elapsedMin(), berryC, coldC,
           lidOpen, cooling, (int)level, kPerMin, unreachable ? -2.0f : etaMin, method, etaAiMin);
  f.close();
}

void handleSerial() {
  if (!Serial.available()) return;
  char c = Serial.read();
  if (c == 'r') resetRun();
  else if (c == 'a') listSensors();
  else if (c == 'd' && fsOk) {
    File f = LittleFS.open("/log.csv", "r");
    if (!f) { Serial.println("기록 없음"); return; }
    while (f.available()) Serial.write(f.read());
    f.close();
    Serial.println("--- 기록 끝 ---");
  } else if (c == 'm') {
    Serial.printf("MODEL ready=%d use=%d info=%s\n", MODEL_READY, USE_AI, MODEL_INFO);
    Serial.printf("CFG,%.2f,%.1f,%d\n", TARGET_C, DEADLINE_MIN, DEMO_MODE);
  } else if (c == 'x' && fsOk) {
    LittleFS.remove("/log.csv");
    Serial.println("기록을 지웠어요");
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n예냉박스 AI 시작");
  Serial.printf("MODEL ready=%d use=%d info=%s\n", MODEL_READY, USE_AI, MODEL_INFO);
  Serial.printf("CFG,%.2f,%.1f,%d\n", TARGET_C, DEADLINE_MIN, DEMO_MODE);   // PC 프로그램이 목표·제한 시간을 맞춤
  if (DEMO_MODE) Serial.printf("[시연 모드] 목표 %.1fC, 제한 %.0f분 (실제 기준은 1C, 60분)\n", TARGET_C, DEADLINE_MIN);

  pinMode(PIN_FAN, OUTPUT); digitalWrite(PIN_FAN, LOW);
  pinMode(PIN_LED_R, OUTPUT); pinMode(PIN_LED_Y, OUTPUT); pinMode(PIN_LED_G, OUTPUT);
  pinMode(PIN_BUZ, OUTPUT); buzzer(false);
  pinMode(PIN_REED, INPUT_PULLUP);

  damper.setPeriodHertz(50);
  damper.attach(PIN_SERVO, 500, 2400);
  damper.write(DAMPER_CLOSED_DEG);

  Wire.begin(21, 22);
  oledOk = oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);   // 안 되면 0x3D로 바꿔 보기
  if (!oledOk) Serial.println("OLED를 못 찾았어요 (배선, 주소 0x3C 확인)");

  fsOk = LittleFS.begin(true);
  if (!fsOk) Serial.println("기록 저장 공간(LittleFS)을 못 열었어요");

  listSensors();
  sensors.requestTemperatures();

  setLeds(1, 0, 0); delay(200); setLeds(0, 1, 0); delay(200); setLeds(0, 0, 1); delay(200);
  buzzer(true); delay(80); buzzer(false);
  bleBegin();   // 블루투스 시작 (앱 연결 대기)
  resetRun();   // 전원을 켠 순간을 "수확 후 0분"으로 봐요
}

void loop() {
  static unsigned long lastReq = 0, lastHist = 0, lastLog = 0, lastPrint = 0, lastDraw = 0, lastBle = 0;
  unsigned long now = millis();
  handleSerial();
  bleHandleCmd();
  bleSendHistStep();

  // 1) 인지
  if (now - lastReq >= 2000) {
    berryC = sensors.getTempC(ADDR_BERRY);
    coldC  = sensors.getTempC(ADDR_COLD);
    sensors.requestTemperatures();
    lastReq = now;
  }
  bool open = digitalRead(PIN_REED) != LID_CLOSED_LEVEL;
  if (open && !lidOpen) lidOpenSince = now;
  lidOpen = open;

  // 2) 판단
  float el = elapsedMin();
  if (validTemp(berryC)) {
    if (!doneLatched && berryC <= TARGET_C) { doneLatched = true; if (doneAtMin < 0) doneAtMin = el; }
    if (doneLatched && berryC > RESTART_C) doneLatched = false;     // 다시 데워지면 다시 식히기
  }
  // 상태 판단에 쓸 예측: AI가 계산되면 AI, 아니면 뉴턴/직선
  if (etaAiMin != -1) { etaUsed = etaAiMin; usedBy = 'A'; }
  else if (unreachable) { etaUsed = -2; usedBy = method; }
  else if (etaMin >= 0) { etaUsed = etaMin; usedBy = method; }
  else { etaUsed = -1; usedBy = '-'; }
  if (!validTemp(berryC)) level = SENSOR_ERR;
  else if (doneLatched) level = DONE;
  else if (el > DEADLINE_MIN) level = LATE;
  else if (usedBy != '-' && (etaUsed == -2 || el + etaUsed > DEADLINE_MIN)) level = LATE_RISK;
  else level = COOLING;

  // 3) 행동: 식히기 (넣자마자 켜고, 목표에 닿으면 끔)
  if (level == SENSOR_ERR || lidOpen) setCooling(false);
  else if (berryC <= FREEZE_GUARD_C) setCooling(false);   // 너무 차가우면 멈춤
  else if (doneLatched) setCooling(false);
  else setCooling(true);

  // 기록·예측 (식히기 상태를 정한 뒤에 기록해야 팬 상태가 맞아요. 예측은 다음 판단부터 쓰여요)
  if (validTemp(berryC) && now - lastHist >= HIST_MS) {
    histT[histHead] = elapsedMin();
    histB[histHead] = berryC;
    histC[histHead] = validTemp(coldC) ? coldC : NAN;
    histOk[histHead] = cooling && !lidOpen;   // 팬 켜짐 + 뚜껑 닫힘일 때만 AI 기울기에 써요
    histHead = (histHead + 1) % N_HIST;
    if (histCount < N_HIST) histCount++;
    updatePrediction();
    updateAi();
    lastHist = now;
    // PC 프로그램용 데이터 줄
    Serial.printf("D,%.3f,%.3f,%.3f,%d,%d,%d,%.2f,%.2f\n", elapsedMin(), berryC,
                  validTemp(coldC) ? coldC : NAN, lidOpen, cooling, (int)level,
                  unreachable ? -2.0f : etaMin, etaAiMin);
  }

  // 3) 행동: 알림
  bool blink = (now / 500) % 2;
  bool beep = false;
  switch (level) {
    case COOLING:    setLeds(0, 1, 0); break;
    case DONE:       setLeds(0, 0, 1); break;
    case LATE_RISK:  setLeds(1, 0, 0); beep = (now % 10000) < 150; break;  // 10초마다 짧게
    case LATE:       setLeds(1, 0, 0); beep = (now % 2000) < 200;  break;  // 2초마다
    case SENSOR_ERR: setLeds(blink, 0, 0); break;
  }
  if (lidOpen && now - lidOpenSince > LID_ALARM_MS) beep = beep || ((now % 1000) < 100);
  buzzer(beep);

  if (now - lastDraw >= 500) { drawOled(); lastDraw = now; }
  if (now - lastBle >= BLE_STATUS_MS) { bleSendStatus(); lastBle = now; }   // 앱으로 상태 보내기
  if (now - lastLog >= 30000) { appendLog(); lastLog = now; }
  if (now - lastPrint >= 2000) {
    Serial.printf("T+%.1fmin berry=%.2f cold=%.2f lid=%s fan=%s level=%d newton=%.1f(%c)%s ai=%.1f use=%c\n",
                  el, berryC, coldC, lidOpen ? "OPEN" : "CLOSED", cooling ? "ON" : "OFF",
                  (int)level, etaMin, method, unreachable ? " UNREACHABLE" : "", etaAiMin, usedBy);
    lastPrint = now;
  }
}
