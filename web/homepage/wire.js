
    (function(){
      var fig=document.querySelector('#p-wire figure.sheet'), box=document.getElementById('wstep');
      var btns=[].slice.call(document.querySelectorAll('.wpick button'));
      var ROW='<p>“D4 단자”는 확장보드에 D4라고 적힌 나사단자예요. 선 끝 피복을 5~6mm 벗겨 단자에 넣고 나사를 조여요. 선은 USB를 뺀 상태에서 연결해요.</p>';
      var S={
        pw:{t:'1. 확장보드와 전원 묶음',p:'<p>ESP32를 MRD068A 나사단자 확장보드에 꽂고, 전원선은 같은 전원끼리 꼬아서 열수축 튜브로 감싼 뒤 단자 하나에 넣어요. 3V3·VIN 단자는 1개씩, GND 단자는 2개예요.</p>',
            s:['ESP32를 확장보드에 꽂아요. 보드 핀 이름과 확장보드에 인쇄된 이름이 같은 자리에 오게 맞춰요. (30핀용 보드)','<b>3.3V 묶음</b> → <b>3V3</b> 단자: 온도센서 빨강 2개, 4.7kΩ 한쪽 다리, 리드스위치·OLED·부저 VCC (모두 6개)','<b>5V 묶음</b> → <b>VIN</b> 단자: 서보 빨강, MOSFET 파란 단자 + 선 (2개)','<b>GND 묶음 ①</b> → 왼쪽 <b>GND</b> 단자: 온도센서 검정 2개, OLED, 서보, MOSFET 초록 GND와 파란 − (6개)','<b>GND 묶음 ②</b> → 오른쪽 <b>GND</b> 단자: 리드스위치, 신호등 LED, 부저 (3개)','꼰 부분은 열수축 튜브로 끝까지 덮고, 한 가닥씩 살짝 당겨서 빠지지 않는지 확인해요.','전원을 켜기 전에 멀티미터로 3.3V·5V·GND 묶음끼리 붙어 있지 않은지(쇼트) 확인해요.']},
        ds:{t:'2. 온도센서 DS18B20 ×2',s:['노랑 선 2개 + 4.7kΩ 한쪽 다리를 함께 꼬아 → <b>D4</b> 단자','빨강 선 2개 → <b>3.3V</b> 묶음','검정 선 2개 → <b>GND</b> 묶음 ①','4.7kΩ 다른 다리 → <b>3.3V</b> 묶음']},
        reed:{t:'3. 리드스위치 모듈',s:['DO → <b>D27</b> 단자','VCC → <b>3.3V</b> 묶음','GND → <b>GND</b> 묶음 ②','5V(VIN)에는 연결하지 않아요. ESP32 입력은 3.3V까지예요.']},
        oled:{t:'4. OLED 0.96인치',s:['SDA → <b>D21</b> 단자','SCL → <b>D22</b> 단자 (사이의 RX0·TX0 단자는 비워요)','VCC → <b>3.3V</b> 묶음','GND → <b>GND</b> 묶음 ①']},
        led:{t:'5. 신호등 LED 모듈',s:['R → <b>D26</b> 단자','Y → <b>D33</b> 단자','G → <b>D32</b> 단자','GND → <b>GND</b> 묶음 ②']},
        buz:{t:'6. 액티브 부저 모듈',s:['I/O → <b>D25</b> 단자','VCC → <b>3.3V</b> 묶음','GND → <b>GND</b> 묶음 ②']},
        servo:{t:'7. SG90 서보',s:['주황 → <b>D18</b> 단자','빨강 → <b>5V</b> 묶음 (VIN 단자)','갈색 → <b>GND</b> 묶음 ①']},
        mos:{t:'8. MOSFET 모듈과 쿨링팬',p:'<p>MOSFET 모듈은 초록 단자(PWM·GND)와 파란 3칸 단자(+ / LOAD / −)가 있어요. +와 −에 전원을 넣고, 팬은 +와 LOAD 사이에 다는 흔한 연결 방식으로 적었어요. 모듈 설명서와 다르면 설명서를 따라요.</p>',s:['초록 단자 PWM → <b>D19</b> 단자','초록 단자 GND → <b>GND</b> 묶음 ①','파란 단자 <b>+</b> → <b>5V</b> 묶음 (VIN 단자)','파란 단자 <b>−</b> → <b>GND</b> 묶음 ①','팬 빨강 → 파란 단자 <b>+</b> (5V 선과 같은 칸에 함께 넣어요)','팬 검정 → 파란 단자 <b>LOAD</b> (가운데 칸)','1N5819를 팬 두 선 사이에 달고, 띠가 있는 쪽을 빨강(+) 쪽에']},
        all:{t:'전체 보기',p:'<p>모든 선이 한 번에 보여요. 실제로 연결할 때는 위 버튼을 1번부터 차례로 눌러서 하나씩 해요.</p>',s:[]}
      };
      var MAP={'w-rails':'pw','w-esp':'pw','w-ds':'ds','w-reed':'reed','w-oled':'oled','w-led':'led','w-buz':'buz','w-servo':'servo','w-mos':'mos','w-fan':'mos'};
      function setK(k){
        fig.setAttribute('data-k',k);
        btns.forEach(function(b){b.setAttribute('aria-pressed',b.getAttribute('data-k')===k?'true':'false');});
        var d=S[k], h='<h3>'+d.t+'</h3>'+(d.p||ROW);
        if(d.s.length) h+='<ol>'+d.s.map(function(x){return '<li>'+x+'</li>';}).join('')+'</ol>';
        box.innerHTML=h;
      }
      btns.forEach(function(b){b.addEventListener('click',function(){setK(b.getAttribute('data-k'));});});
      fig.addEventListener('click',function(e){
        var g=e.target.closest('[data-w]'); if(!g) return;
        var k=MAP[g.getAttribute('data-w')]; if(k) setK(k);
      });
      setK('pw');
    })();
    