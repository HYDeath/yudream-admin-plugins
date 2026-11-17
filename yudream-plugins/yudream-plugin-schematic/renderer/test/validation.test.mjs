import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateScene} from '../validation.mjs';
const scene=()=>({size:[1,1,1],blocks:[{x:0,y:0,z:0,state:'minecraft:hopper',properties:{facing:'down',enabled:'true'}}]});
test('accepts bounded state data including directional properties',()=>assert.equal(validateScene(scene()).blocks[0].properties.facing,'down'));
test('rejects scripts, bad coordinates, duplicate positions and oversized scenes',()=>{
  for(const edit of [s=>s.blocks[0].state='<script>alert(1)</script>',s=>s.blocks[0].x=1,s=>s.blocks.push({...s.blocks[0]}),s=>s.size[0]=1e9,s=>s.blocks[0].properties.facing='</script>',s=>s.blocks[0].properties=null]){
    const s=scene();edit(s);assert.throws(()=>validateScene(s));
  }
});
