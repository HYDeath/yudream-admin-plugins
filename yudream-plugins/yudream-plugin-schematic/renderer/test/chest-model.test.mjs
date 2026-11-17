import test from 'node:test';
import assert from 'node:assert/strict';
import {chestRenderState, doubleChestMesh} from '../chest-model.mjs';

const id = name => 'minecraft:' + name;
const close = (a, b) => assert.ok(Math.abs(a - b) < 1e-6, `${a} != ${b}`);
const points = mesh => mesh.quads.flatMap(q => q.vertices().map(v => v.pos.components()));

test('double chest halves join with a single centered latch for all four facings', () => {
  const placements = {
    north: [[0, 0, 0], [1, 0, 0]], west: [[0, 0, 1], [0, 0, 0]],
    south: [[1, 0, 0], [0, 0, 0]], east: [[0, 0, 0], [0, 0, 1]],
  };
  for (const [facing, positions] of Object.entries(placements)) {
    const axis = facing === 'north' || facing === 'south' ? 0 : 2;
    const halves = ['left', 'right'].map((type, i) => {
      const mesh = doubleChestMesh(id('chest'), {type, facing}, {getTextureUV: () => [0, 0, 1, 1]});
      // No hidden internal joining face on the body, lid or latch.
      assert.equal(mesh.quads.length, 15);
      const world = points(mesh).map(p => p.map((v, a) => v + positions[i][a]));
      const latch = points({quads: mesh.quads.slice(10)}).map(p => p.map((v, a) => v + positions[i][a]));
      return {world, latch};
    });
    const ranges = halves.map(h => [Math.min(...h.world.map(p => p[axis])), Math.max(...h.world.map(p => p[axis]))]).sort((a, b) => a[0] - b[0]);
    close(ranges[0][0], 1 / 16); close(ranges[0][1], 1);
    close(ranges[1][0], 1); close(ranges[1][1], 31 / 16);
    const latch = halves.flatMap(h => h.latch);
    close(Math.min(...latch.map(p => p[axis])), 15 / 16);
    close(Math.max(...latch.map(p => p[axis])), 17 / 16);
  }
});

test('uses each double chest texture and preserves singles, ender chests and modded blocks', () => {
  for (const [name, texture] of Object.entries({chest: 'normal', trapped_chest: 'trapped', copper_chest: 'copper', waxed_oxidized_copper_chest: 'copper_oxidized'})) {
    for (const type of ['left', 'right']) {
      const used = new Set();
      doubleChestMesh(id(name), {type, facing: 'north'}, {getTextureUV: key => {used.add(key.toString()); return [0, 0, 1, 1];}});
      assert.deepEqual([...used], [id(`entity/chest/${texture}_${type}`)]);
      assert.ok(chestRenderState(id(name), {type}).startsWith('schematic:'));
    }
    assert.equal(chestRenderState(id(name), {type: 'single'}), id(name));
  }
  assert.equal(chestRenderState(id('ender_chest'), {type: 'left'}), id('ender_chest'));
  assert.equal(chestRenderState('mod:chest', {type: 'left'}), 'mod:chest');
});
