
    (function(){
      var fig=document.querySelector('#p-wire figure.sheet'), box=document.getElementById('wstep');
      var btns=[].slice.call(document.querySelectorAll('.wpick button'));
      var ROW='<p>“D4 줄”은 보드의 D4 핀이 꽂힌 가로줄이에요. 그 줄의 빈 구멍에 꽂으면 D4에 연결돼요. 선은 USB를 뺀 상태에서 꽂아요.</p>';
      var S={
        pw:{t:'1. 전원 줄 만들기',p:'<p>먼저 보드의 전원을 브레드보드 가운데 +/− 줄로 옮겨요. 가운데 줄은 보드 밑에 가려지니 USB 쪽 위로 드러난 구멍에 꽂아요.</p>',
            s:['<b>3V3</b> 줄 → 가운데 <b>왼쪽 +</b> 줄. 이제 이 줄이 3.3V 줄이에요.','왼쪽 <b>GND</b> 줄 → 가운데 <b>왼쪽 −</b> 줄','<b>VIN</b> 줄 → 가운데 <b>오른쪽 +</b> 줄. 이제 이 줄이 5V 줄이에요.','오른쪽 <b>GND</b> 줄 → 가운데 <b>오른쪽 −</b> 줄','두 + 줄이 바뀌지 않게 + 줄에 이름 테이프를 붙여 둬요.']},
        ds:{t:'2. 온도센서 DS18B20 ×2',s:['노랑 선 2개 → <b>D4</b> 줄','빨강 선 2개 → <b>3.3V</b> 줄 (가운데 왼쪽 +)','검정 선 2개 → <b>GND</b> 줄 (가운데 −)','4.7kΩ 저항: 한쪽 다리 → <b>D4</b> 줄, 다른 다리 → <b>3.3V</b> 줄']},
        reed:{t:'3. 리드스위치 모듈',s:['DO → <b>D27</b> 줄','VCC → <b>3.3V</b> 줄','GND → <b>GND</b> 줄','5V 줄에는 꽂지 않아요. ESP32 입력은 3.3V까지예요.']},
        oled:{t:'4. OLED 0.96인치',s:['SDA → <b>D21</b> 줄','SCL → <b>D22</b> 줄 (사이의 RX0·TX0 줄은 비워요)','VCC → <b>3.3V</b> 줄','GND → <b>GND</b> 줄']},
        led:{t:'5. 신호등 LED 모듈',s:['R → <b>D26</b> 줄','Y → <b>D33</b> 줄','G → <b>D32</b> 줄','GND → <b>GND</b> 줄']},
        buz:{t:'6. 액티브 부저 모듈',s:['I/O → <b>D25</b> 줄','VCC → <b>3.3V</b> 줄','GND → <b>GND</b> 줄']},
        servo:{t:'7. SG90 서보',s:['주황 → <b>D18</b> 줄','빨강 → <b>5V</b> 줄 (가운데 오른쪽 +)','갈색 → <b>GND</b> 줄']},
        mos:{t:'8. MOSFET 모듈과 쿨링팬',s:['PWM → <b>D19</b> 줄','VIN+ → <b>5V</b> 줄','VIN− (GND) → <b>GND</b> 줄','팬 빨강 → <b>OUT+</b>, 검정 → <b>OUT−</b>','1N5819를 팬 두 선 사이에 달고, 띠가 있는 쪽을 <b>OUT+</b>에']},
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
    