from pathlib import Path
import re,json
s=Path('app/app/src/main/java/ai/mwm/client/ui/theme/Color.kt').read_text()
def rgb(v):return tuple(int(v[i:i+2],16)/255 for i in (2,4,6))
def lum(c):return sum(w*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4) for w,v in zip((.2126,.7152,.0722),c))
def ratio(a,b):x,y=sorted((lum(a),lum(b)));return (y+.05)/(x+.05)
results=[]
for mode,section in zip(('Light','Night'),s.split('= ')[1:]):pass
for mode,section in [('Light',s.split('val LightAppColorScheme = ')[1].split('val DarkAppColorScheme')[0]),('Night',s.split('val DarkAppColorScheme = ')[1])]:
 colors={k:rgb(v) for k,v in re.findall(r'(\w+) = Color\(0x([A-Fa-f0-9]{8})\)',section)}
 for bg in ['background','surface','surfaceVariant','surfaceContainerLow']:
  # Outlines distinguish common cards and outlined controls from their surroundings.
  for name,minimum in [('outline',3),('onSurface',4.5),('onSurfaceVariant',4.5)]:
   value=ratio(colors[name],colors[bg]);assert value>=minimum,(mode,name,bg,value)
   results.append((mode,name,bg,round(value,2)))
 for fg,bg in [('onPrimary','primary'),('onSecondary','secondary'),('onTertiary','tertiary')]:
  value=ratio(colors[fg],colors[bg]);assert value>=4.5,(mode,fg,bg,value)
  results.append((mode,fg,bg,round(value,2)))
Path('.v137_verification/contrast-results.json').write_text(json.dumps(results,indent=2))
print(len(results),'representative theme contrast checks passed; minimum outline:',min(x[3] for x in results if x[1]=='outline'))
