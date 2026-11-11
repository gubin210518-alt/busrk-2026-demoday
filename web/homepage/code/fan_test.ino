/*
  팬 시험 코드 (MOSFET 모듈 + 5V 팬)  -  TEAM 부스럭
  -------------------------------------------------
  하는 일: 팬을 5초 켜고 5초 끄기를 반복해요.
          시리얼 모니터(115200)에 지금 상태를 찍어요.

  연결 (USB를 뺀 상태에서 연결):
    MOSFET 모듈 초록 단자  PWM -> D19,  GND -> GND 줄
    MOSFET 모듈 파란 단자  +   -> 5V 줄
                          LOAD(가운데) -> 팬 검정
                          -   -> GND 줄
    팬 빨강 -> 5V 줄
    1N5819: 팬 빨강~검정 사이, 띠가 있는 쪽이 빨강(+) 쪽

  확인할 것:
    "FAN ON"이 찍힐 때 팬이 힘차게 돌면 성공.
    약하게 돌거나 안 돌면, 이 모듈이 5V 전원에서 MOSFET을
    충분히 못 켜는 것일 수 있어요(판매 페이지 기준 최소 6V).
*/

const int FAN_PIN = 19;   // 보드의 D19

void setup() {
  Serial.begin(115200);
  pinMode(FAN_PIN, OUTPUT);
  digitalWrite(FAN_PIN, LOW);   // 처음엔 꺼 둠
  delay(500);
  Serial.println("팬 시험 시작: 5초 켜고 5초 끄기 반복");
}

void loop() {
  digitalWrite(FAN_PIN, HIGH);
  Serial.println("FAN ON  (D19 = HIGH)");
  delay(5000);

  digitalWrite(FAN_PIN, LOW);
  Serial.println("FAN OFF (D19 = LOW)");
  delay(5000);
}
