# AI 시뮬레이터(HTML 한 파일) 빌드
# app_template.html 안의 /*CORE*/ 자리에 core.js, /*MODEL*/ 자리에 default_model.json을 넣어
# ../web/simulator/index.html 을 만든다. (현재 배포본과 바이트 단위로 같게 나오는 것 확인함)
import os
here = os.path.dirname(os.path.abspath(__file__))
p = lambda *a: os.path.join(here, *a)
t = open(p('app_template.html'), encoding='utf-8').read()
core = open(p('core.js'), encoding='utf-8').read()
model = open(p('default_model.json'), encoding='utf-8').read()
t = t.replace('/*CORE*/', core).replace('/*MODEL*/', model)
out = p('..', 'web', 'simulator', 'index.html')
open(out, 'w', encoding='utf-8').write(t)
print('wrote', out)
