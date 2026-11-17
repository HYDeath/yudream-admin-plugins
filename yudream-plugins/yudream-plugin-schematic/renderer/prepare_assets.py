"""Extract JSON/PNG data from a checksum-verified official client JAR. Never load classes."""
import argparse, hashlib, io, json, math, zipfile
from pathlib import Path
from PIL import Image

parser=argparse.ArgumentParser()
parser.add_argument('jar',type=Path)
parser.add_argument('--sha1',required=True)
parser.add_argument('--output',type=Path,default=Path(__file__).parent/'assets')
args=parser.parse_args()
assert hashlib.sha1(args.jar.read_bytes()).hexdigest()==args.sha1, 'Official client checksum mismatch'
args.output.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.jar) as jar:
    version=json.loads(jar.read('version.json'))
    states={};models={};textures=[]
    for path in sorted(jar.namelist()):
        prefix='assets/minecraft/'
        if not path.startswith(prefix):continue
        rel=path[len(prefix):]
        if rel.startswith('blockstates/') and rel.endswith('.json'):
            states['minecraft:'+rel[len('blockstates/'):-5]]=json.loads(jar.read(path))
        elif rel.startswith('models/block/') and rel.endswith('.json'):
            models['minecraft:'+rel[len('models/'):-5]]=json.loads(jar.read(path))
        elif rel.startswith(('textures/block/','textures/entity/')) and rel.endswith('.png'):
            image=Image.open(io.BytesIO(jar.read(path))).convert('RGBA')
            # Freeze the first animation frame; entity textures keep their original dimensions.
            if path+'.mcmeta' in jar.namelist():
                animation=json.loads(jar.read(path+'.mcmeta')).get('animation',{})
                w=animation.get('width',image.width);h=animation.get('height',w)
                first=animation.get('frames',[0])[0];first=first.get('index',0) if isinstance(first,dict) else first
                x=first*w%image.width;y=first*w//image.width*h
                image=image.crop((x,y,x+w,y+h))
            textures.append(('minecraft:'+rel[len('textures/'):-4],image))
    # Deterministic shelf atlas with full resolution, including special block-entity textures.
    width=2048;x=y=row=0;placements=[]
    for key,image in sorted(textures,key=lambda entry:(-entry[1].height,entry[0])):
        w,h=image.size
        if x+w>width:x=0;y+=row;row=0
        placements.append((key,image,x,y));x+=w;row=max(row,h)
    height=2**math.ceil(math.log2(max(16,y+row)))
    atlas=Image.new('RGBA',(width,height));uv={}
    for key,image,x,y in placements:
        atlas.paste(image,(x,y));uv[key]=[x/width,y/height,(x+image.width)/width,(y+image.height)/height]
    atlas.save(args.output/'atlas.png',optimize=True)
    resources={'version':version['id'],'dataVersion':version['world_version'],'clientSha1':args.sha1,'states':states,'models':models,'uv':uv}
    (args.output/'resources.json').write_text(json.dumps(resources,separators=(',',':')),encoding='utf-8')
    print(json.dumps({'version':version['id'],'blockstates':len(states),'models':len(models),'textures':len(textures),'atlas':[width,height]}))
