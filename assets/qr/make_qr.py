import segno, io
from PIL import Image, ImageDraw
import zxingcpp
icon=Image.open('fav/icon_1024.png').convert('RGBA')
def make(url,out):
    q=segno.make(url,error='h',micro=False)
    buf=io.BytesIO(); q.save(buf,kind='png',scale=24,border=4,dark='#13202a'); buf.seek(0)
    img=Image.open(buf).convert('RGBA'); W=img.size[0]
    s=int(W*0.22); pad=int(s*0.12)
    bg=Image.new('RGBA',(s+2*pad,s+2*pad),(0,0,0,0)); d=ImageDraw.Draw(bg)
    d.rounded_rectangle([0,0,s+2*pad-1,s+2*pad-1],radius=pad*2,fill='white')
    ic=icon.resize((s,s),Image.LANCZOS); bg.alpha_composite(ic,(pad,pad))
    o=(W-bg.size[0])//2; img.alpha_composite(bg,(o,o))
    img.convert('RGB').save(out)
    r=zxingcpp.read_barcodes(Image.open(out))
    print(out, img.size, [x.text for x in r])
make('https://busrk-box3d.vercel.app/about.html','qr/부스럭_홈페이지_QR.png')
make('https://busrk-ai-studio.vercel.app','qr/부스럭_AI시뮬레이터_QR.png')
