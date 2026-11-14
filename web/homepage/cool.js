/* 페이지가 식어요: 내린 만큼 수확 후 시간이 흐르고(0→60분), 식품칸 온도가 뉴턴 냉각 곡선을 따라 25℃에서 1℃로 내려가요.
   실측이 아니라 목표 곡선을 보여 주는 장식이에요. */
(function(){
  var r=document.documentElement, el=document.getElementById('thermo');
  var tv=el&&el.querySelector('.tv'), tm=el&&el.querySelector('.tm'), kt=el&&el.querySelector('.kt');
  var K=0.05, E60=Math.exp(-60*K);
  function T(m){ return 1+24*(Math.exp(-K*m)-E60)/(1-E60); }
  var busy=false;
  function up(){
    busy=false;
    var max=r.scrollHeight-innerHeight, p=max>8?Math.min(1,Math.max(0,scrollY/max)):0;
    r.style.setProperty('--p',p.toFixed(3));
    if(!el) return;
    var m=p*60, t=T(m), done=m>=59.5;
    tv.textContent=(done?1:t).toFixed(1)+'℃';
    tm.textContent='수확 후 '+Math.round(m)+'분';
    kt.textContent=done?'식품칸 · 완료':'식품칸 · 식히는 중';
    el.dataset.s=done?'g':'y';
    el.style.setProperty('--fill',Math.max(2,(t-1)/24*100).toFixed(1)+'%');
  }
  function q(){ if(!busy){ busy=true; requestAnimationFrame(up); } }
  addEventListener('scroll',q,{passive:true}); addEventListener('resize',q); up();
})();
