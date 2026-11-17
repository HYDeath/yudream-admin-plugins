import {BlockDefinition,BlockModel,Identifier,Mesh,Structure,StructureRenderer,TextureAtlas} from 'deepslate';
import {mat4,vec3} from 'gl-matrix';
import {chestDefinitions,chestRenderState} from './chest-model.mjs';

let resources,raw,models,definitions;
export async function initialize(data,atlasUrl) {
  raw=data;models={};definitions={};
  for(const [id,json] of Object.entries(data.models))models[id]=BlockModel.fromJson(json);
  for(const [id,json] of Object.entries(data.states))definitions[id]=BlockDefinition.fromJson(json);
  Object.assign(definitions,chestDefinitions());
  const accessor={getBlockModel:id=>models[id.toString()]};
  for(const model of Object.values(models))model.flatten(accessor);
  const image=new Image();image.src=atlasUrl;await image.decode();
  const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
  const ctx=canvas.getContext('2d');ctx.drawImage(image,0,0);
  const atlas=new TextureAtlas(ctx.getImageData(0,0,image.width,image.height),data.uv);
  resources={...accessor,
    getBlockDefinition:id=>definitions[id.toString()],
    getTextureAtlas:()=>atlas.getTextureAtlas(),
    getTextureUV:id=>atlas.getTextureUV(id),
    getPixelSize:()=>1/image.width,
    // Conservative culling retains thin/special model faces and transparent interiors.
    getBlockFlags:id=>({opaque:false,self_culling:/glass|water|ice/.test(id.path),semi_transparent:/glass|water|ice|slime|honey/.test(id.path)}),
    getBlockProperties:()=>null,getDefaultBlockProperties:()=>null,
  };
  return {version:data.version};
}

class ModelRenderer extends StructureRenderer {
  setOrthographic(width,height) {mat4.ortho(this.projMatrix,-width/2,width/2,-height/2,height/2,.01,100000);}
}

export function render(scene) {
  const structure=new Structure(scene.size);const missing=new Set();let stateVariants=new Set();
  for(const b of scene.blocks) {
    if(!definitions[b.state])missing.add(b.state);
    structure.addBlock([b.x,b.y,b.z],chestRenderState(b.state,b.properties),b.properties);
    stateVariants.add(b.state+JSON.stringify(b.properties));
  }
  // Unknown modded blocks are reported rather than silently replaced with unrelated cubes.
  if(missing.size)throw new Error('资源包中没有这些方块：'+[...missing].slice(0,12).join('、'));
  const output=document.getElementById('scene');output.replaceChildren();
  const canvas=document.createElement('canvas');canvas.width=1600;canvas.height=640;canvas.style.cssText='width:1600px;height:640px;display:block';output.append(canvas);
  const gl=canvas.getContext('webgl',{alpha:true,antialias:true,preserveDrawingBuffer:true,premultipliedAlpha:false});
  if(!gl)throw new Error('浏览器无法创建 WebGL 渲染上下文');
  const renderer=new ModelRenderer(gl,structure,resources,{useInvisibleBlockBuffer:false,chunkSize:8});
  const center=scene.size.map(v=>v/2);
  const radius=Math.hypot(...scene.size)+10;
  const corners=[];
  for(const x of [0,scene.size[0]])for(const y of [0,scene.size[1]])for(const z of [0,scene.size[2]])corners.push([x,y,z]);
  gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  for(let side=0;side<2;side++) {
    const angle=(side===0?Math.PI/4:Math.PI*5/4);
    const eye=[center[0]+radius*Math.cos(angle),center[1]+radius*.72,center[2]+radius*Math.sin(angle)];
    const view=mat4.lookAt(mat4.create(),eye,center,[0,1,0]);
    const projected=corners.map(p=>vec3.transformMat4(vec3.create(),p,view));
    const spanX=Math.max(...projected.map(p=>p[0]))-Math.min(...projected.map(p=>p[0]));
    const spanY=Math.max(...projected.map(p=>p[1]))-Math.min(...projected.map(p=>p[1]));
    const orthoHeight=Math.max(spanY,spanX/(800/640))*1.12;
    renderer.setViewport(side*800,0,800,640);renderer.setOrthographic(orthoHeight*800/640,orthoHeight);
    renderer.drawStructure(view);
  }
  gl.finish();
  const meshes=renderer.chunkBuilder.getMeshes();
  const quads=meshes.reduce((n,mesh)=>n+mesh.quads.length,0);
  if(!quads)throw new Error('投影没有生成可见模型');
  output.dataset.ready='true';
  return {blocks:scene.blocks.length,stateVariants:stateVariants.size,quads,resourceVersion:raw.version,width:1600,height:640};
}
