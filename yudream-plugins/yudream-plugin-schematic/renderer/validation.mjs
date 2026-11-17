export function validateScene(scene) {
  if(!scene || !Array.isArray(scene.size)||scene.size.length!==3 || scene.size.some(v=>!Number.isSafeInteger(v)||v<1||v>8192))throw new Error('无效的投影尺寸');
  if(!Array.isArray(scene.blocks)||!scene.blocks.length||scene.blocks.length>350000)throw new Error('方块数须为 1–350000');
  const occupied=new Set();
  for(const b of scene.blocks) {
    if(!b || ![b.x,b.y,b.z].every((v,i)=>Number.isSafeInteger(v)&&v>=0&&v<scene.size[i]))throw new Error('方块坐标越界');
    if(typeof b.state!=='string'||b.state.length>256||!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(b.state))throw new Error('无效的方块名称');
    if(!b.properties || Array.isArray(b.properties)||typeof b.properties!=='object'||Object.keys(b.properties).length>64)throw new Error('无效的方块属性');
    for(const [key,value]of Object.entries(b.properties))if(!/^[a-z0-9_]{1,64}$/.test(key)||typeof value!=='string'||!/^[a-z0-9_.-]{1,128}$/.test(value))throw new Error('无效的方块属性');
    const key=`${b.x},${b.y},${b.z}`;if(occupied.has(key))throw new Error('方块坐标重复');occupied.add(key);
  }
  return scene;
}
