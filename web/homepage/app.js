
(function(){
'use strict';
var stage=document.getElementById('stage'), canvas=document.getElementById('c');
function esc(s){return String(s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
function f1(v){return (Math.round(v*10)/10).toString();}

/* ---------- 부품 설명 ---------- */
var META=[
 {id:1,name:'칸막이 (폼보드 5mm)',short:'냉기실과 식품칸을 나눠요',
  role:'박스 안을 왼쪽 냉기실(아이스팩)과 오른쪽 식품칸으로 나눠요. 가운데 창이 냉기가 지나가는 유일한 통로예요.',
  where:'왼쪽 안쪽 벽에서 약 10cm 떨어진 곳에, 박스 안쪽 폭에 꽉 끼게 세워요.',
  fix:'안쪽 치수보다 2~3mm 작게 잘라 끼우고, 틈은 양면 폼테이프나 핫멜트로 막아요.',
  link:'전기 연결은 없어요.',
  care:'틈이 있으면 냉기가 새서 센서 값이 흔들려요. 위쪽은 1.5cm 정도 비워 두어 선이 지나가게 해요. 냉기실 쪽 면에는 뽁뽁이(12번)를 붙여 단열해요.'},
 {id:2,name:'아이스팩',short:'냉기실에 세워 둬요',
  role:'냉기를 내는 부분이에요. 딸기에 직접 닿지 않고 칸막이 뒤에서 공기만 차갑게 해요.',
  where:'냉기실 안, 왼쪽 벽에 붙여 세워요. 6개 세트 중 들어가는 만큼 넣어요.',
  fix:'따로 붙이지 않고 세워 둬요. 쓰러지면 폼보드 조각으로 받쳐요.',
  link:'전기 연결은 없어요.',
  care:'아이스팩과 칸막이 사이를 2~3cm 띄워서 팬이 공기를 빨아들일 자리를 남겨요.'},
 {id:3,name:'팬 40mm 5V',short:'창 앞에서 식품칸으로 바람을 밀어요',
  role:'MOSFET 모듈이 켜고 꺼요. 댐퍼가 열려 있을 때 냉기실의 찬 공기를 식품칸으로 밀어요. 딸기를 넣자마자 켜지고, 목표 온도에 닿으면 꺼져요.',
  where:'칸막이 창의 가운데, 냉기실 쪽 면이에요.',
  fix:'팬 모서리 구멍에 케이블 타이를 걸거나 핫멜트로 칸막이에 붙여요.',
  link:'빨강은 MOSFET 파란 단자 +(5V 선과 같은 칸), 검정은 LOAD에 연결해요.',
  care:'팬 옆면의 바람 방향 화살표가 식품칸 쪽을 향해야 해요. 모듈에 다이오드가 없으면 1N5819를 팬 두 선 사이에 달아요.',
  wires:['w-fan','w-mos']},
 {id:4,name:'댐퍼 플랩 + 서보 SG90',short:'창을 여닫아서 냉기량을 조절해요',
  role:'서보가 플랩을 돌려 창을 열고 닫아요. 닫으면 냉기 이동이 크게 줄어요.',
  where:'플랩은 창 위쪽 모서리에 달고, 서보는 창 옆 칸막이의 식품칸 쪽 면에 붙여요. 서보 축과 플랩 경첩이 한 줄에 오게 해요.',
  fix:'서보는 양면 폼테이프에 핫멜트를 더해 고정하고, 플랩(폼보드)은 서보 혼에 접착해요.',
  link:'주황은 D18 단자, 빨강은 5V 묶음, 갈색은 GND 묶음이에요.',
  care:'닫힘 0도, 열림 약 55도는 가정한 값이에요. 실제로 움직여 보며 정하고, 닫혔을 때 틈이 없는지 확인해요.',
  wires:['w-servo']},
 {id:5,name:'온도센서 T1 (식품칸)',short:'딸기 가까이 공중에 매달아요',
  role:'딸기가 있는 곳의 공기 온도를 재요. 목표 온도(1℃) 도달과 남은 시간 예측은 이 값으로 해요.',
  where:'식품칸 가운데, 딸기 팩에서 1~2cm 떨어진 공중이에요. 딸기 속 온도를 직접 재는 방법은 앞으로 개선할 점이에요.',
  fix:'선을 칸막이 위쪽 모서리에 테이프로 고정해서 매달아요.',
  link:'노랑 DATA는 D4 단자(4.7kΩ으로 3.3V 묶음에 연결), 빨강은 3.3V 묶음, 검정은 GND 묶음이에요.',
  care:'두 센서가 같은 데이터선을 써요. 선에 테이프 라벨(T1, T2)을 붙이고, 코드에서 센서 주소로 구분해요.',
  wires:['w-ds']},
 {id:6,name:'온도센서 T2 (냉기실)',short:'아이스팩 옆 공기를 재요',
  role:'냉기실이 얼마나 차가운지 재요. 이 값으로 식품칸이 목표 온도까지 내려갈 수 있는지, 몇 분이 걸릴지 계산해요.',
  where:'아이스팩과 칸막이(뽁뽁이) 사이 빈 곳, 아이스팩에서 1~2cm 떨어진 곳이에요.',
  fix:'선을 위쪽에서 테이프로 고정해 매달아요.',
  link:'T1과 같은 세 선에 병렬로 연결해요.',
  care:'아이스팩 표면이나 뽁뽁이에 닿으면 공기 온도와 달라져요.',
  wires:['w-ds']},
 {id:7,name:'배선 구멍',short:'안쪽 선이 한 번에 밖으로 나가요',
  role:'센서 2개, 팬, 서보 선을 바깥 전장 케이스로 빼요.',
  where:'왼쪽 벽 윗부분, 케이스 뒤쪽이에요.',
  fix:'선을 통과시킨 뒤 구멍을 실리콘이나 핫멜트로 막아 냉기가 새지 않게 해요.',
  link:'선이 많아서 점퍼선을 이어 붙일 때는 이음매를 테이프로 감싸요.',
  care:'구멍을 낼 때 칼이나 열을 쓰는 도구를 써요. 어른이나 선생님과 함께 천천히 해요.'},
 {id:8,name:'전장 케이스',short:'ESP32(HW-394) · MRD068A 확장보드 · MOSFET 모듈',
  role:'두뇌와 나사단자 확장보드, 팬 스위치가 들어 있는 상자예요. 투명하게 그려서 안이 보여요.',
  where:'박스 바깥 왼쪽 면, 배선 구멍 바로 앞이에요.',
  fix:'벨크로나 두꺼운 양면테이프로 붙여요.',
  link:'ESP32는 MRD068A 나사단자 확장보드에 꽂아요. 전원선은 같은 전원끼리 꼬아 열수축 튜브로 감싼 뒤 3V3·VIN·GND 단자에 넣어요. MOSFET은 초록 단자 PWM이 D19, 파란 단자 +가 5V 묶음, −가 GND 묶음이에요.',
  care:'차가운 안쪽에 두면 물방울이 맺혀 쇼트가 날 수 있어서 바깥에 뒀어요.',
  wires:['w-esp','w-rails','w-mos']},
 {id:9,name:'표시부 (OLED · 신호등 LED · 부저)',short:'케이스 덮개 바깥에 붙어요',
  role:'식품칸 온도와 목표까지 남은 시간을 보여주고, 신호등 LED와 부저로 알려요.',
  where:'케이스 덮개 바깥쪽이에요. OLED는 왼쪽, 신호등 모듈은 가운데, 부저는 오른쪽 아래예요.',
  fix:'덮개에 창을 내서 끼우거나 양면테이프로 붙이고, 선은 덮개 안쪽에서 확장보드 단자로 이어요.',
  link:'OLED SDA·SCL은 D21·D22, LED R·Y·G는 D26·D33·D32, 부저 I/O는 D25예요. 전원은 모두 3.3V 묶음이에요.',
  care:'OLED 핀은 납땜이 필요할 수 있어요. 부저는 켜자마자 잠깐 울릴 수 있어서 코드 첫 줄에서 D25(GPIO25)를 HIGH로 둬요.',
  wires:['w-oled','w-led','w-buz']},
 {id:10,name:'리드스위치 + 자석',short:'뚜껑이 열렸는지 알아요',
  role:'뚜껑이 닫히면 자석이 리드스위치 모듈 바로 위에 와서 닫힘 신호가 나와요.',
  where:'모듈은 박스 앞면 윗부분 오른쪽, 자석은 뚜껑 앞쪽 아래에 같은 위치로 붙여요.',
  fix:'둘 다 양면테이프로 붙이고, 선은 바깥 벽을 따라 케이스까지 테이프로 고정해요.',
  link:'DO는 D27 단자, VCC는 3.3V 묶음, GND는 GND 묶음이에요.',
  care:'둘 사이 간격이 1cm 안쪽에서 동작하는지 먼저 시험해요. 자석이 모듈에 같이 오는지 확인해요.',
  wires:['w-reed']},
 {id:11,name:'5V 충전기 (USB-C)',short:'케이스 USB 선을 콘센트에 연결해요',
  role:'ESP32에 전원을 주고, ESP32 VIN에서 나온 5V가 서보와 팬에도 가요.',
  where:'박스 옆 바닥이에요.',
  fix:'선이 박스 옆으로 처지지 않게 케이블 타이나 테이프로 한 번 고정해요.',
  link:'USB-C 케이블로 ESP32 보드의 USB 포트에 연결해요.',
  care:'서보와 팬이 같이 돌 때 보드가 재부팅되면 5V 2A 어댑터를 5V 묶음에 따로 넣고 GND를 함께 묶어요.',
  wires:['w-esp']},
 {id:12,name:'뽁뽁이 단열층 (에어캡)',short:'냉기실 쪽 칸막이 면을 덮어요',
  role:'냉기실과 식품칸이 맞닿는 칸막이 면을 단열해요. 댐퍼를 닫았을 때 칸막이를 통해 냉기가 새는 것을 줄여서, 팬과 댐퍼로 식품칸 온도를 조절할 수 있게 해요.',
  where:'냉기실 안, 칸막이의 냉기실 쪽 면 전체예요. 가운데 창(팬 자리)만 뚫어 둬요.',
  fix:'칸막이 크기에 맞게 자르고 창 부분을 오려 낸 뒤, 테이프로 가장자리를 붙여요.',
  link:'전기 연결은 없어요.',
  care:'창을 막으면 바람이 지나가지 못해요. 팬과 온도센서 T2가 뽁뽁이에 눌리거나 닿지 않게 해요.'}
];
var metaById={}; META.forEach(function(m){metaById[m.id]=m;});

/* ---------- 탭 ---------- */
var tab='3d';
var tabs={'3d':document.getElementById('t-3d'),wire:document.getElementById('t-wire')};
var panels={'3d':document.getElementById('p-3d'),wire:document.getElementById('p-wire')};
function setTab(name){
  tab=name;
  Object.keys(tabs).forEach(function(k){
    var on=k===name;
    tabs[k].setAttribute('aria-selected',on?'true':'false'); tabs[k].tabIndex=on?0:-1;
    panels[k].hidden=!on;
  });
  if(name==='3d'&&window.__resize3d) window.__resize3d();
  if(sel) showDetail(sel);
}
tabs['3d'].addEventListener('click',function(){setTab('3d');});
tabs.wire.addEventListener('click',function(){setTab('wire');});
document.querySelector('.tabs').addEventListener('keydown',function(e){
  if(e.key==='ArrowRight'||e.key==='ArrowLeft'){var n=tab==='3d'?'wire':'3d'; setTab(n); tabs[n].focus(); e.preventDefault();}
});

/* ---------- 목록과 상세 ---------- */
var plist=document.getElementById('plist'), detail=document.getElementById('detail'), pinsEl=document.getElementById('pins');
var sel=4;
META.forEach(function(m){
  var li=document.createElement('li'), b=document.createElement('button');
  b.type='button'; b.dataset.id=m.id;
  b.innerHTML='<span class="n">'+m.id+'</span><span class="t"><b>'+esc(m.name)+'</b><i>'+esc(m.short)+'</i></span>';
  li.appendChild(b); plist.appendChild(li);
  var p=document.createElement('button');
  p.type='button'; p.className='pin'; p.textContent=m.id; p.dataset.id=m.id; p.title=m.name; p.setAttribute('aria-label',m.id+'번 '+m.name);
  pinsEl.appendChild(p);
});
function hlWires(id){
  document.querySelectorAll('.wm').forEach(function(g){g.classList.remove('hl');});
  (metaById[id].wires||[]).forEach(function(w){document.querySelectorAll('[data-w="'+w+'"]').forEach(function(g){g.classList.add('hl');});});
}
function showDetail(id){
  var m=metaById[id];
  var go='';
  if(tab==='3d'&&m.wires) go='<button class="tg go" type="button" data-go="wire">배선도에서 보기</button>';
  if(tab==='wire') go='<button class="tg go" type="button" data-go="3d">3D에서 위치 보기</button>';
  detail.innerHTML='<h2><span class="n">'+m.id+'</span>'+esc(m.name)+'</h2><dl>'+
    '<dt>역할</dt><dd>'+esc(m.role)+'</dd>'+
    '<dt>위치</dt><dd>'+esc(m.where)+'</dd>'+
    '<dt>고정</dt><dd>'+esc(m.fix)+'</dd>'+
    '<dt>연결</dt><dd>'+esc(m.link)+'</dd>'+
    '<dt>주의</dt><dd class="care">'+esc(m.care)+'</dd></dl>'+go;
  plist.querySelectorAll('button').forEach(function(b){
    if(+b.dataset.id===id) b.setAttribute('aria-current','true'); else b.removeAttribute('aria-current');
  });
  hlWires(id);
}
detail.addEventListener('click',function(e){
  var b=e.target.closest('[data-go]'); if(!b) return;
  setTab(b.dataset.go);
  if(b.dataset.go==='3d'&&window.__select3d) window.__select3d(sel,true);
  (b.dataset.go==='wire'?panels.wire:panels['3d']).scrollIntoView({behavior:'smooth',block:'start'});
});
var selectHook=null;
function select(id,fly){ sel=id; showDetail(id); if(selectHook) selectHook(id,fly); }
plist.addEventListener('click',function(e){var b=e.target.closest('button[data-id]'); if(b) select(+b.dataset.id,true);});
pinsEl.addEventListener('click',function(e){var b=e.target.closest('button[data-id]'); if(b) select(+b.dataset.id,true);});
document.querySelectorAll('.wm').forEach(function(g){
  g.addEventListener('click',function(){var id=+g.dataset.part; if(id) select(id,false);});
});

/* ---------- 박스 치수 ---------- */
var DEF={W:40,D:28,H:26,t:2.5};
var LIM={W:[30,70],D:[22,50],H:[18,45],t:[1.5,4]};
var dims=Object.assign({},DEF);
try{var sv=JSON.parse(localStorage.getItem('busrk-dims')||'null'); if(sv&&sv.W) dims=sv;}catch(e){}
var inp={W:document.getElementById('dW'),D:document.getElementById('dD'),H:document.getElementById('dH'),t:document.getElementById('dT')};
var dOut=document.getElementById('dOut');
function fillInputs(){Object.keys(inp).forEach(function(k){inp[k].value=dims[k];});}
function geo(d){
  var W=d.W,D=d.D,H=d.H,t=d.t;
  var iw=W-2*t, id=D-2*t, y0=t, ih=H-t;
  var g={W:W,D:D,H:H,t:t,iw:iw,id:id,y0:y0,ih:ih,xL:-iw/2,xR:iw/2,dt:.5};
  g.cw=Math.max(7,Math.min(10,iw*.3));
  g.xd=g.xL+g.cw; g.fx=g.xd+g.dt; g.yT=H-1.5;
  g.ws=Math.max(5,Math.min(8,id-7,ih-8));
  g.wc=y0+Math.max(g.ws/2+3,Math.min(ih*.55,(g.yT-y0)-3-g.ws/2));
  g.cwid=Math.min(22,D-2);
  g.cy=Math.min(H-6.5,Math.max(7,H*.62));
  g.hy=g.cy+3.3;
  g.vol=iw*id*ih/1000;
  return g;
}
function renderCut(g){
  var dh=g.yT-g.y0, fw=g.ws+1.4, fh=g.ws+.6;
  var a3=(Math.max(g.id,dh)<=42&&Math.min(g.id,dh)<=29.7);
  var rows=[
    ['박스 안쪽',f1(g.iw)+' × '+f1(g.id)+' × '+f1(g.ih)+'cm','안쪽 부피 약 '+f1(g.vol)+'L예요. 신청서에는 약 5~10L로 적혀 있어요.'],
    ['칸막이 판',f1(g.id)+' × '+f1(dh)+'cm','폼보드 5mm예요. 왼쪽 안쪽 벽에서 '+f1(g.cw)+'cm 떨어진 곳에 세워요. '+(a3?'A3 한 장에서 나와요.':'<span class="warn">A3(29.7 × 42cm) 한 장보다 커서 이어 붙여야 해요.</span>')],
    ['칸막이 창',f1(g.ws)+' × '+f1(g.ws)+'cm','안쪽 바닥에서 '+f1(g.wc-g.ws/2-g.y0)+'cm부터 '+f1(g.wc+g.ws/2-g.y0)+'cm 높이, 판의 가운데예요.'],
    ['댐퍼 플랩',f1(fw)+' × '+f1(fh)+'cm','창보다 조금 크게 잘라 닫혔을 때 가장자리가 겹치게 해요.'],
    ['배선 구멍','지름 약 12mm','왼쪽 벽 가운데, 바깥 바닥에서 '+f1(g.hy)+'cm 높이예요. 선 5가닥이 지나가는지 실제 선으로 대어 봐요.'],
    ['리드스위치','위치','앞면 오른쪽 끝에서 6cm, 윗면에서 1cm 아래예요. 자석은 뚜껑 앞쪽 같은 자리에 붙여요.']
  ];
  var cutEl=document.getElementById('cut'); if(cutEl) cutEl.innerHTML=rows.map(function(r){return '<tr><td>'+r[0]+'</td><td class="num">'+r[1]+'</td><td>'+r[2]+'</td></tr>';}).join('');
  dOut.innerHTML='안쪽 <b>'+f1(g.iw)+' × '+f1(g.id)+' × '+f1(g.ih)+'cm</b>, 부피 약 <b>'+f1(g.vol)+'L</b>. 실제 박스를 재서 넣으면 모형이 바뀌어요.';
}
var rebuildHook=null;
function readDims(){
  var nd={}, bad=[];
  var names={W:'가로',D:'세로',H:'높이',t:'벽 두께'};
  Object.keys(inp).forEach(function(k){
    var v=parseFloat(inp[k].value);
    if(!isFinite(v)||v<LIM[k][0]||v>LIM[k][1]) bad.push(names[k]+'는 '+LIM[k][0]+'~'+LIM[k][1]+'cm');
    nd[k]=v;
  });
  if(!bad.length&&(nd.H-nd.t<14)) bad.push('안쪽 높이가 14cm보다 커야 해요');
  if(bad.length){ dOut.innerHTML='<span class="err">'+esc(bad.join(', '))+' 사이로 넣어 주세요.</span>'; return; }
  dims=nd;
  try{localStorage.setItem('busrk-dims',JSON.stringify(dims));}catch(e){}
  var g=geo(dims); renderCut(g); if(rebuildHook) rebuildHook(g);
}
var dTimer=null;
Object.keys(inp).forEach(function(k){
  inp[k].addEventListener('input',function(){clearTimeout(dTimer); dTimer=setTimeout(readDims,400);});
});
document.getElementById('dims').addEventListener('submit',function(e){e.preventDefault(); readDims();});
document.getElementById('dReset').addEventListener('click',function(){dims=Object.assign({},DEF); fillInputs(); readDims();});
fillInputs(); renderCut(geo(dims));

if(location.hash==='#wire') setTab('wire');

/* ---------- 3D ---------- */
var reduce=window.matchMedia&&matchMedia('(prefers-reduced-motion: reduce)').matches;
var T=window.THREE, renderer, scene, camera, controls;
var okGL=!!(T&&T.OrbitControls);
if(okGL){ try{ renderer=new T.WebGLRenderer({canvas:canvas,antialias:true,alpha:true}); }catch(e){ okGL=false; } }
if(!okGL){
  stage.insertAdjacentHTML('beforeend','<p class="fail">3D 화면을 열지 못했어요. 새로고침하거나 다른 브라우저로 열어 주세요. 배선도 탭과 아래 목록은 그대로 볼 수 있어요.</p>');
  pinsEl.hidden=true; showDetail(sel);
  return;
}
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
renderer.setClearColor(0x000000,0);
scene=new T.Scene();
camera=new T.PerspectiveCamera(36,1,1,800);
controls=new T.OrbitControls(camera,canvas);
controls.enableDamping=true; controls.dampingFactor=.08; controls.minDistance=18; controls.maxDistance=240;
controls.maxPolarAngle=Math.PI*.495;
scene.add(new T.HemisphereLight(0xffffff,0x8a9aa6,.95));
var dl=new T.DirectionalLight(0xffffff,.6); dl.position.set(-30,70,55); scene.add(dl);
var root=new T.Group(); scene.add(root);

function css(n){return getComputedStyle(document.documentElement).getPropertyValue(n).trim();}
function M(c,o){return new T.MeshStandardMaterial(Object.assign({color:c,roughness:.65,metalness:.05},o||{}));}
function edge(mesh,op){
  var e=new T.LineSegments(new T.EdgesGeometry(mesh.geometry),new T.LineBasicMaterial({color:0x3d4d57,transparent:true,opacity:op==null?.4:op}));
  mesh.add(e); return e;
}
function B(w,h,d,mat,x,y,z,par,noEdge){
  var m=new T.Mesh(new T.BoxGeometry(w,h,d),mat); m.position.set(x,y,z); (par||root).add(m);
  if(!noEdge) edge(m); return m;
}
function C(r,h,mat,x,y,z,par,axis){
  var m=new T.Mesh(new T.CylinderGeometry(r,r,h,28),mat); m.position.set(x,y,z);
  if(axis==='x') m.rotation.z=Math.PI/2; if(axis==='z') m.rotation.x=Math.PI/2;
  (par||root).add(m); return m;
}
function tube(pts,col,r,par){
  var cv=new T.CatmullRomCurve3(pts.map(function(p){return new T.Vector3(p[0],p[1],p[2]);}));
  var m=new T.Mesh(new T.TubeGeometry(cv,Math.max(20,pts.length*14),r||.1,6,false),M(col,{roughness:.8}));
  (par||root).add(m); return m;
}

/* 재료 (다시 지어도 재사용) */
var mFront=M('#bccad3',{transparent:true,opacity:.97,roughness:.9,side:T.DoubleSide});
var mLeft=M('#bccad3',{transparent:true,opacity:.97,roughness:.9,side:T.DoubleSide});
var mOther=M('#bccad3',{roughness:.9,side:T.DoubleSide});
var mLid=M('#e4ecf0',{transparent:true,opacity:.62,roughness:.8,depthWrite:false});

var parts={}, lid, flapG, blades, air, magnet, G=null, views={};
function part(id,anchor,parent,inner){
  var g=new T.Group(); (parent||root).add(g);
  parts[id]={g:g,anchor:new T.Vector3(anchor[0],anchor[1],anchor[2]),inner:!!inner,extra:[]}; return g;
}
function clearRoot(){
  root.traverse(function(o){
    if(o.geometry) o.geometry.dispose();
    if(o.material&&[mFront,mLeft,mOther,mLid].indexOf(o.material)<0){
      if(o.material.map) o.material.map.dispose();
      o.material.dispose();
    }
  });
  while(root.children.length) root.remove(root.children[0]);
  parts={}; saved=new WeakMap();
}

function build(g){
  clearRoot(); G=g;
  var W=g.W,D=g.D,H=g.H,t=g.t,xL=g.xL,xR=g.xR,id=g.id,ih=g.ih,y0=g.y0;
  var xd=g.xd,fx=g.fx,dt=g.dt,ws=g.ws,wc=g.wc,yT=g.yT;
  var wy=t+(H-t)/2, wh=H-t;

  /* 본체 */
  B(W,t,D,mOther,0,t/2,0);
  B(t,wh,D,mLeft,-W/2+t/2,wy,0);
  B(t,wh,D,mOther,W/2-t/2,wy,0);
  B(g.iw,wh,t,mOther,0,wy,-D/2+t/2);
  B(g.iw,wh,t,mFront,0,wy,D/2-t/2);

  /* 뚜껑 */
  lid=new T.Group(); lid.position.set(0,H,-D/2); root.add(lid);
  B(W,3,D,mLid,0,1.5,D/2,lid);
  B(Math.min(10,W*.25),1.1,2.4,M('#a9b9c3'),0,3.55,D-4,lid);
  var rx=W/2-6;
  magnet=C(.55,.5,M('#c0392b'),rx,.35,D+.25,lid,'z');

  /* 1 칸막이 */
  var mFoam=M('#f3f4f1',{roughness:.95});
  var g1=part(1,[xd+dt/2,yT+.8,-id*.32],null,true);
  var lowH=(wc-ws/2)-y0, upH=yT-(wc+ws/2), sideW=(id-ws)/2;
  B(dt,lowH,id,mFoam,xd+dt/2,y0+lowH/2,0,g1);
  B(dt,upH,id,mFoam,xd+dt/2,wc+ws/2+upH/2,0,g1);
  B(dt,ws,sideW,mFoam,xd+dt/2,wc,-(ws/2+sideW/2),g1);
  B(dt,ws,sideW,mFoam,xd+dt/2,wc,(ws/2+sideW/2),g1);

  /* 12 뽁뽁이 단열층 (냉기실 쪽 칸막이 면, 창은 뚫림) */
  var bt=.45, bx=xd-bt/2-.02;
  var bc=document.createElement('canvas'); bc.width=bc.height=64; var bx2=bc.getContext('2d');
  bx2.fillStyle='#e6f2f8'; bx2.fillRect(0,0,64,64);
  for(var bi=0;bi<2;bi++)for(var bj=0;bj<2;bj++){var cx=16+bi*32+(bj%2)*16, cy=16+bj*32;
    var gr=bx2.createRadialGradient(cx-4,cy-4,2,cx,cy,13); gr.addColorStop(0,'#ffffff'); gr.addColorStop(1,'#b8d7e6');
    bx2.fillStyle=gr; bx2.beginPath(); bx2.arc(cx%64,cy,12,0,7); bx2.fill();}
  var btex=new T.CanvasTexture(bc); btex.wrapS=btex.wrapT=T.RepeatWrapping; btex.repeat.set(id/2.2,(yT-y0)/2.2);
  var mBub=M('#ffffff',{map:btex,transparent:true,opacity:.82,roughness:.3});
  var g12=part(12,[bx-.3,y0+lowH*.45,-id*.3],null,true);
  B(bt,lowH,id,mBub,bx,y0+lowH/2,0,g12);
  B(bt,upH,id,mBub,bx,wc+ws/2+upH/2,0,g12);
  B(bt,ws,sideW,mBub,bx,wc,-(ws/2+sideW/2),g12);
  B(bt,ws,sideW,mBub,bx,wc,(ws/2+sideW/2),g12);

  /* 2 아이스팩 */
  var mIce=M('#8fd0ee',{transparent:true,opacity:.9,roughness:.35});
  var ph=Math.min(ih-4,14), pw=Math.min(id/2-1.5,9), pxc=xL+.4+1.25, pR=xL+.4+2.5, pz=pw/2+.5;
  var g2=part(2,[pxc,y0+ph+.9,-pz],null,true);
  B(2.5,ph,pw,mIce,pxc,y0+ph/2,-pz,g2);
  B(2.5,ph,pw,mIce,pxc,y0+ph/2,pz,g2);

  /* 3 팬 */
  var fanX=xd-.5;
  var g3=part(3,[fanX,wc+2.9,-1.6],null,true); g3.position.set(fanX,wc,0);
  B(1,4,4,M('#2b3238'),0,0,0,g3);
  var ring=new T.Mesh(new T.TorusGeometry(1.85,.12,8,36),M('#14181c')); ring.rotation.y=Math.PI/2; ring.position.x=.52; g3.add(ring);
  blades=new T.Group(); blades.position.x=.55; g3.add(blades);
  C(.7,.4,M('#1c2126'),0,0,0,blades,'x');
  for(var i=0;i<7;i++){
    var arm=new T.Group(); arm.rotation.x=i*Math.PI*2/7; blades.add(arm);
    var bl=B(.06,1.3,.75,M('#47555f'),0,1.15,0,arm,true); bl.rotation.x=.5;
  }

  /* 4 댐퍼 플랩 + 서보 */
  var hx=fx+.6, hyy=wc+ws/2+.2, fwid=ws+1.4, fht=ws+.6, sz=fwid/2+.2;
  var g4=part(4,[hx,hyy+1.9,sz+1.1],null,true);
  flapG=new T.Group(); flapG.position.set(hx,hyy,0); g4.add(flapG);
  B(.4,fht,fwid,M('#e0953a'),-.2,-fht/2,0,flapG);
  B(.35,1.8,.18,M('#f2f2ee'),-.15,-.6,fwid/2+.1,flapG);
  B(1.2,2.25,2.27,M('#2f6db3'),hx,hyy-.555,sz+1.135,g4);
  B(1.2,3.2,.2,M('#2f6db3'),hx,hyy-.555,sz+1.6,g4);
  C(.45,.4,M('#f2f2ee'),hx,hyy,sz-.2,g4,'z');

  /* 5, 6 온도센서 */
  function probe(pid,x,y,z){
    var gp=part(pid,[x,y+3.6,z],null,true);
    C(.3,3.2,M('#c8d0d6',{metalness:.8,roughness:.3}),x,y,z,gp);
    C(.38,1.2,M('#16191c'),x,y+2.1,z,gp);
    return gp;
  }
  var fw=xR-fx, s=Math.min(1,fw/24,id/23,ih/22);
  var t1x=fx+fw*.5, t1z=id*.05, t1y=y0+ih*.5;
  var t2x=(pR+(xd-1))/2, t2z=pz, t2y=wc-2;
  probe(5,t1x,t1y,t1z);
  probe(6,t2x,t2y,t2z);

  /* 딸기 팩 (예시) */
  var pw=9*s, ph=4*s, pd=7*s;
  var packMat=M('#e8f1f4',{transparent:true,opacity:.35});
  var berryMat=M('#d23a3f',{roughness:.5}), leafMat=M('#3f8a3a');
  [[fx+fw*.38,-id*.18],[fx+fw*.72,id*.2]].forEach(function(p){
    B(pw,ph,pd,packMat,p[0],y0+ph/2,p[1]);
    var r=Math.min(pw/7,pd/5.5);
    for(var i=0;i<3;i++) for(var j=0;j<2;j++){
      var bx=p[0]-pw/3+i*pw/3, bz=p[1]-pd/4+j*pd/2, by=y0+r*1.1;
      var b=new T.Mesh(new T.SphereGeometry(r,12,10),berryMat); b.scale.set(1,1.15,1); b.position.set(bx,by,bz); root.add(b);
      C(r*.55,r*.25,leafMat,bx,by+r*1.15,bz);
    }
  });

  /* 케이블 (박스 안) */
  var hy=g.hy, Hh=[xL+.1,hy,0], top=H-.9;
  var sx=hx, sTop=hyy+.57;
  tube([[sx,sTop,sz+1.1],[sx,top,sz+1.1],[xd-3,top,sz],[xL+.4,H-1.6,1.6],[xL+.25,hy+1.5,.7],Hh],'#d98a3d',.1);
  tube([[fanX-.5,wc+1.9,.3],[fanX-1.3,H-2.5,.3],[xL+3,top,-.8],[xL+.4,H-1.6,-1.4],[xL+.25,hy+1.5,-.6],Hh],'#1a1d20',.1);
  tube([[fanX-.5,wc+1.9,-.3],[fanX-1.3,H-2.5,-.3],[xL+3,top+.1,-1.1],[xL+.4,H-1.5,-1.8],[xL+.25,hy+1.5,-.9],Hh],'#c0392b',.1);
  tube([[t2x,t2y+2.7,t2z],[t2x,top,t2z],[xL+2,top,t2z*.7],[xL+.4,H-1.6,2.6],[xL+.25,hy+1.3,1],Hh],'#6e7b84',.1);
  tube([[t1x,t1y+2.7,t1z],[t1x,top+.1,t1z],[fx+2,top+.2,t1z*.8],[xd-2,top+.1,.2],[xL+.4,H-1.6,.3],[xL+.25,hy+1.5,.2],Hh],'#6e7b84',.1);

  /* 7 배선 구멍 */
  var g7=part(7,[xL+.2,hy-2.4,0],null,true);
  C(.8,.25,M('#101214'),xL+.05,hy,0,g7,'x');
  var seal=new T.Mesh(new T.TorusGeometry(1.0,.14,8,28),M('#d98a3d')); seal.rotation.y=Math.PI/2; seal.position.set(xL+.15,hy,0); g7.add(seal);
  tube([[xL+.1,hy,0],[-W/2,hy,0],[-W/2-.6,hy,0]],'#101214',.35,g7);

  /* 8 전장 케이스: 왼쪽 벽 바깥, 로컬 +z가 바깥, 로컬 +x가 앞쪽 */
  var cw=g.cwid, cy=g.cy;
  var g8=part(8,[-W/2-2.5,cy-6.9,cw/2-2],null,false); g8.position.set(-W/2,cy,0); g8.rotation.y=-Math.PI/2;
  var mCase=M('#9fb0ba',{transparent:true,opacity:.34,depthWrite:false,side:T.DoubleSide});
  B(cw,12,.3,M('#9fb0ba',{transparent:true,opacity:.6,side:T.DoubleSide}),0,0,.15,g8);
  B(cw,.3,5,mCase,0,5.85,2.5,g8); B(cw,.3,5,mCase,0,-5.85,2.5,g8);
  B(.3,12,5,mCase,-cw/2+.15,0,2.5,g8); B(.3,12,5,mCase,cw/2-.15,0,2.5,g8);
  B(cw,12,.2,M('#cfe0ea',{transparent:true,opacity:.2,depthWrite:false,side:T.DoubleSide}),0,0,5,g8);
  /* MRD068A 나사단자 확장보드(약 6.6×6.3cm): 양옆에 나사단자 줄, 가운데에 ESP32 */
  B(6.6,6.3,.16,M('#1f4f8f'),5,-2,.38,g8);
  B(6.4,.75,.75,M('#2f7d4f'),5,.75,.84,g8); B(6.4,.75,.75,M('#2f7d4f'),5,-4.75,.84,g8);
  B(5.2,.28,.8,M('#15181b'),5,-.4,.86,g8); B(5.2,.28,.8,M('#15181b'),5,-3.6,.86,g8);
  /* ESP32 HW-394 (USB-C가 앞쪽) */
  B(5.1,2.8,.15,M('#1d2a33'),5,-2,1.95,g8);
  B(3.6,.3,.7,M('#15181b'),5.2,-.85,1.5,g8); B(3.6,.3,.7,M('#15181b'),5.2,-3.15,1.5,g8);
  B(2.4,1.8,.28,M('#9aa5ad',{metalness:.8,roughness:.35}),3.4,-2,2.15,g8);
  B(.75,.9,.35,M('#b9c0c5',{metalness:.8,roughness:.3}),7.75,-2,2.1,g8);
  /* MOSFET 모듈 */
  B(3.4,2.4,.18,M('#1f5fa8'),-5.5,3.2,.5,g8);
  B(1.2,1.0,.9,M('#2c63c8'),-7.3,3.75,1.0,g8); B(1.2,1.0,.9,M('#2c63c8'),-7.3,2.65,1.0,g8);
  B(.9,.8,.5,M('#111'),-4.6,3.2,.85,g8);
  /* 케이스 안 점퍼선 (일부만) */
  tube([[0,3.3,.6],[-2,3.6,1.1],[-5,3.9,1.3],[-7.3,3.75,1.5]],'#c0392b',.09,g8);
  tube([[.2,3.2,.6],[-2,2.9,1.0],[-5,2.7,1.2],[-7.3,2.65,1.5]],'#1a1d20',.09,g8);
  tube([[.4,3.3,.6],[1.6,2.2,1.3],[3.2,-.4,1.5]],'#6e7b84',.08,g8);
  tube([[.6,3.3,.6],[2.2,2.5,1.4],[4.2,-.4,1.5]],'#f2c230',.08,g8);
  tube([[.8,3.3,.6],[2.8,2.8,1.5],[5.4,-.4,1.5]],'#d98a3d',.08,g8);
  tube([[-4.2,3.0,.7],[-1.5,1.0,1.4],[2.6,-3.6,1.5]],'#2259c9',.08,g8);
  /* USB-C 선 → 충전기 */
  var chZ=D/2+8, chX=-W/2-6.8, lyc=3-cy;
  tube([[8.1,-2,2.1],[cw/2+.8,-2,2.2],[cw/2+2.4,-6,3.4],[D/2+3,lyc*.6,5.6],[D/2+5,lyc+.8,6.6],[chZ-2.05,lyc,6.8]],'#15181b',.28,g8);

  /* 9 표시부 (덮개 바깥) */
  var g9=part(9,[-W/2-5.4,cy+3.1,-5],g8,false);
  B(2.7,2.8,.25,M('#1b3f6b'),-5,.4,5.2,g9);
  B(2.5,1.6,.1,M('#050607'),-5,.5,5.38,g9);
  var scr=new T.Mesh(new T.PlaneGeometry(2.3,1.2),new T.MeshBasicMaterial({map:oledTex()})); scr.position.set(-5,.5,5.45); g9.add(scr);
  B(2.0,5.4,.16,M('#1a1d20'),1.2,.3,5.2,g9);
  [['#e53935',2.0],['#fdd835',.3],['#43a047',-1.4]].forEach(function(l){
    var sph=new T.Mesh(new T.SphereGeometry(.42,18,12),M(l[0],{emissive:new T.Color(l[0]),emissiveIntensity:.45,roughness:.3}));
    sph.position.set(1.2,l[1],5.42); g9.add(sph);
  });
  B(3.3,1.6,.16,M('#123a6b'),6.3,-3.3,5.2,g9);
  C(.6,.8,M('#15171a'),6.6,-3.3,5.65,g9,'z');
  C(.16,.82,M('#4a5056'),6.6,-3.3,5.66,g9,'z');
  [['#f2c230',[-5,1.6]],['#43a047',[-5,-.6]],['#c0392b',[1.2,2.6]],['#2259c9',[6.3,-3.3]]].forEach(function(w){
    tube([[4.5,-1.2,1.6],[1.5,1.5,3.2],[w[1][0]+.4,w[1][1],4.95]],w[0],.07,g8);
  });

  /* 10 리드스위치 + 자석 */
  var g10=part(10,[rx,H+1.6,D/2+.6],null,false);
  B(3.2,1.5,.5,M('#8a2b2b'),rx,H-1,D/2+.25,g10);
  C(.2,2.2,M('#bfe6f2',{transparent:true,opacity:.75}),rx,H-.9,D/2+.62,g10,'x');
  B(.5,.5,.2,M('#2a2d30'),rx-1.1,H-1.4,D/2+.55,g10,true);
  parts[10].extra.push(magnet);
  tube([[rx-1.6,H-1.2,D/2+.4],[-W/2+3,H-1.3,D/2+.35],[-W/2-.4,H-1.5,D/2-1],[-W/2-2.5,cy+6.4,cw/2-1.5]],'#8a2b2b',.08);

  /* 11 충전기 */
  var g11=part(11,[chX,6,chZ],null,false);
  B(4,4.5,4,M('#f4f4f2'),chX,2.25,chZ,g11);
  B(1.0,.4,.1,M('#15171a'),chX,3,chZ-2.02,g11,true);
  B(.35,1.0,.15,M('#b7bec3',{metalness:.7}),chX+.6,2.4,chZ+2.1,g11,true);
  B(.35,1.0,.15,M('#b7bec3',{metalness:.7}),chX-.6,2.4,chZ+2.1,g11,true);

  /* 공기 흐름 */
  air=new T.Group(); root.add(air);
  var al=Math.max(4,Math.min(8,fw*.33)), o=ws*.3;
  [[0,0],[-o,-o],[-o,o],[o,-o],[o,o]].forEach(function(p){
    air.add(new T.ArrowHelper(new T.Vector3(1,0,0),new T.Vector3(fx+.9,wc+p[0],p[1]),al,0x2aa8d6,1.8,1.1));
  });
  air.add(new T.ArrowHelper(new T.Vector3(1,0,0),new T.Vector3(pR+.3,wc,0),Math.max(1.2,fanX-.5-(pR+.3)),0x2aa8d6,1,.8));

  /* 시점 */
  var S=Math.max(W,D*1.25,H*1.4)/40;
  views={
    all:{pos:[-58*S,50*S,86*S],tgt:[-3*S,H*.85,0]},
    front:{pos:[-3*S,H*.75,100*S],tgt:[-3*S,H*.55,0]},
    left:{pos:[-W/2-64*S,H*.95,12*S],tgt:[-W/2-2,cy,0]},
    top:{pos:[-6*S,108*S,14*S],tgt:[-3*S,H*.4,0]}
  };
  makeGrid(S);
  applyLook();
  if(sel&&parts[sel]) setHL(sel);
}
function oledTex(){
  var c=document.createElement('canvas'); c.width=256; c.height=128; var x=c.getContext('2d');
  x.fillStyle='#02060a'; x.fillRect(0,0,256,128);
  x.fillStyle='#ffd23f'; x.font='bold 24px "IBM Plex Mono",monospace'; x.fillText('ETA 24m',12,26);
  x.fillStyle='#7fe3ff'; x.font='bold 30px "IBM Plex Mono",monospace'; x.fillText('BERRY 8.2C',12,72); x.fillText('AIR -2.1C',12,112);
  return new T.CanvasTexture(c);
}

/* 바닥 격자 */
var grid=null, gridS=1;
function makeGrid(S){
  if(S) gridS=S;
  if(grid){ scene.remove(grid); grid.geometry.dispose(); grid.material.dispose(); }
  var col=new T.Color(css('--grid'));
  grid=new T.GridHelper(160*gridS,32,col,col); grid.position.y=-.02; scene.add(grid);
}

/* ---------- 상태 ---------- */
var st={lid:true,cut:true,dmp:true,fan:true,pins:true};
var LID_OPEN=-1.0, DMP_OPEN=.96, lidC=LID_OPEN, flapC=DMP_OPEN, tw=null;
function flyTo(pos,tgt){
  if(reduce){camera.position.copy(pos); controls.target.copy(tgt); return;}
  tw={pos:pos.clone(),tgt:tgt.clone()};
}
function flyView(name){var v=views[name]; flyTo(new T.Vector3(v.pos[0],v.pos[1],v.pos[2]),new T.Vector3(v.tgt[0],v.tgt[1],v.tgt[2]));}
controls.addEventListener('start',function(){tw=null;});

var status=document.getElementById('status');
function setStatus(){
  var t;
  if(st.dmp&&st.fan) t='댐퍼 열림, 팬 켜짐: 냉기실의 찬 공기를 식품칸으로 밀어 넣어요. 딸기를 식히는 중이에요.';
  else if(st.dmp) t='댐퍼 열림, 팬 꺼짐: 찬 공기가 창으로 천천히 넘어가요.';
  else t='댐퍼 닫힘: 창이 막혀서 냉기 이동이 크게 줄어요. 팬이 켜져 있어도 바람이 식품칸으로 거의 나가지 않아요. 목표 온도에 닿으면 이 상태가 돼요.';
  status.innerHTML=esc(t)+'<small>실제로 얼마나 효과가 나는지는 아직 실험 전이에요.</small>';
}
function applyLook(){
  [mFront,mLeft].forEach(function(m){m.opacity=st.cut?.14:.97; m.depthWrite=!st.cut; m.needsUpdate=true;});
  mLid.opacity=st.lid?.62:.5;
  updatePins();
}
function btn(id,key,onTxt,offTxt,cb){
  var b=document.getElementById(id);
  function paint(){b.setAttribute('aria-pressed',st[key]?'true':'false'); b.textContent=st[key]?onTxt:offTxt;}
  b.addEventListener('click',function(){st[key]=!st[key]; paint(); if(cb) cb(); setStatus();});
  paint(); return paint;
}
var repaintLid=btn('b-lid','lid','뚜껑 열림','뚜껑 닫힘',applyLook);
btn('b-cut','cut','앞·왼쪽 벽 투명','앞·왼쪽 벽 불투명',applyLook);
btn('b-dmp','dmp','댐퍼 열림','댐퍼 닫힘');
btn('b-fan','fan','팬 켜짐','팬 꺼짐');
btn('b-pin','pins','번호 표시','번호 숨김',function(){pinsEl.hidden=!st.pins;});
document.querySelectorAll('[data-view]').forEach(function(b){
  b.addEventListener('click',function(){
    if(b.dataset.view==='top'&&!st.lid){st.lid=true; repaintLid(); applyLook();}
    flyView(b.dataset.view);
  });
});

/* ---------- 강조 ---------- */
var saved=new WeakMap();
function forEachMat(id,fn){
  var p=parts[id]; if(!p) return;
  p.g.traverse(function(o){ if(o.isMesh&&o.material&&o.material.emissive) fn(o.material); });
  p.extra.forEach(function(m){ if(m.material&&m.material.emissive) fn(m.material); });
}
function clearHL(id){
  forEachMat(id,function(mat){var s=saved.get(mat); if(s){mat.emissive.copy(s.c); mat.emissiveIntensity=s.i; saved.delete(mat);}});
}
function setHL(id){
  var col=new T.Color(css('--accent'));
  forEachMat(id,function(mat){ if(!saved.has(mat)) saved.set(mat,{c:mat.emissive.clone(),i:mat.emissiveIntensity}); mat.emissive.copy(col); });
}
var hlId=null;
function select3d(id,fly){
  if(hlId) clearHL(hlId);
  hlId=id; setHL(id); updatePins();
  if(fly&&tab==='3d'){
    var b=new T.Box3().setFromObject(parts[id].g);
    parts[id].extra.forEach(function(m){b.expandByObject(m);});
    var c=b.getCenter(new T.Vector3()), dir=camera.position.clone().sub(controls.target).normalize();
    var dist=Math.max(40,camera.position.distanceTo(controls.target)*.7);
    flyTo(c.clone().add(dir.multiplyScalar(dist)),c);
  }
}
selectHook=select3d; window.__select3d=select3d;

var pinEls={};
pinsEl.querySelectorAll('.pin').forEach(function(p){pinEls[p.dataset.id]=p;});
function updatePins(){
  Object.keys(pinEls).forEach(function(k){
    var p=pinEls[k];
    p.classList.toggle('sel',+k===sel);
    p.classList.toggle('dim',!!(parts[k]&&parts[k].inner)&&!st.cut);
  });
}

/* ---------- 크기, 테마, 반복 ---------- */
function resize(){
  var w=stage.clientWidth, h=stage.clientHeight; if(!w||!h) return;
  renderer.setSize(w,h,false); camera.aspect=w/h;
  camera.fov=camera.aspect<1?54:36; camera.updateProjectionMatrix();
}
window.__resize3d=resize;
if(window.ResizeObserver) new ResizeObserver(resize).observe(stage); else window.addEventListener('resize',resize);

function recolor(){ makeGrid(); if(hlId){ clearHL(hlId); setHL(hlId); } }
try{ matchMedia('(prefers-color-scheme: dark)').addEventListener('change',recolor); }catch(e){}
new MutationObserver(recolor).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});

rebuildHook=function(g){ build(g); flyView('all'); };
build(geo(dims));
resize();
var v0=views.all; camera.position.set(v0.pos[0],v0.pos[1],v0.pos[2]); controls.target.set(v0.tgt[0],v0.tgt[1],v0.tgt[2]); controls.update();
setStatus(); select(sel,false);

var tmp=new T.Vector3(), clock=new T.Clock();
function frame(){
  requestAnimationFrame(frame);
  var dt=Math.min(clock.getDelta(),.05), t=clock.elapsedTime;
  if(tab!=='3d') return;
  var k=reduce?1:Math.min(1,dt*6);
  lidC+=((st.lid?LID_OPEN:0)-lidC)*k; flapC+=((st.dmp?DMP_OPEN:0)-flapC)*k;
  lid.rotation.x=lidC; flapG.rotation.z=flapC;
  if(st.fan&&!reduce) blades.rotation.x-=dt*16;
  air.visible=st.fan&&st.dmp;
  if(tw){
    camera.position.lerp(tw.pos,.09); controls.target.lerp(tw.tgt,.09);
    if(camera.position.distanceTo(tw.pos)<.15&&controls.target.distanceTo(tw.tgt)<.15) tw=null;
  }
  controls.update();
  if(hlId){ var pulse=reduce?.4:.3+.22*Math.sin(t*3.2); forEachMat(hlId,function(mat){ if(saved.has(mat)) mat.emissiveIntensity=pulse; }); }
  renderer.render(scene,camera);
  if(st.pins){
    var w=stage.clientWidth, h=stage.clientHeight;
    Object.keys(pinEls).forEach(function(k2){
      var p=pinEls[k2], pp=parts[k2]; if(!pp){p.style.display='none'; return;}
      tmp.copy(pp.anchor).project(camera);
      if(tmp.z>1||tmp.z<-1){p.style.display='none'; return;}
      p.style.display='';
      p.style.transform='translate('+((tmp.x*.5+.5)*w).toFixed(1)+'px,'+((-tmp.y*.5+.5)*h).toFixed(1)+'px) translate(-50%,-50%)';
    });
  }
}
frame();
})();
