import {BlockModel, Cull} from 'deepslate';
import {mat4} from 'gl-matrix';

const textures = new Map(Object.entries({
  chest: 'normal', trapped_chest: 'trapped', copper_chest: 'copper',
  exposed_copper_chest: 'copper_exposed', weathered_copper_chest: 'copper_weathered',
  oxidized_copper_chest: 'copper_oxidized', waxed_copper_chest: 'copper',
  waxed_exposed_copper_chest: 'copper_exposed', waxed_weathered_copper_chest: 'copper_weathered',
  waxed_oxidized_copper_chest: 'copper_oxidized',
}).map(([name, texture]) => ['minecraft:' + name, texture]));

// Private model IDs bypass Deepslate's single-chest special renderer. The input
// retains the real block ID and properties; adjacent single chests stay single.
export function chestRenderState(state, properties) {
  return textures.has(state) && ['left', 'right'].includes(properties?.type)
    ? 'schematic:' + state.slice('minecraft:'.length) : state;
}

function box(from, to, textureOffset, seam) {
  const w = to[0] - from[0], h = to[1] - from[1], d = to[2] - from[2];
  const [u, v] = textureOffset;
  const face = (uv, rotation = 0) => ({texture: '#chest', uv: uv.map(n => n / 4), rotation});
  const faces = {
    north: face([u + d + w + d, v + d, u + d + w + d + w, v + d + h], 180),
    east: face([u + d, v + d, u, v + d + h], 180),
    south: face([u + d, v + d, u + d + w, v + d + h], 180),
    west: face([u + d + w + d, v + d, u + d + w, v + d + h], 180),
    up: face([u + d + w, v, u + d + w + w, v + d], 180),
    down: face([u + d, v, u + d + w, v + d], 180),
  };
  delete faces[seam];
  return {from, to, faces};
}

export function doubleChestMesh(state, properties, atlas) {
  const texture = textures.get(state);
  if (!texture || !['left', 'right'].includes(properties?.type)) throw new Error('Invalid double chest');
  const left = properties.type === 'left';
  // Canonical north-facing halves meet exactly at the block boundary. Each
  // contributes half the center latch and omits the internal joining face.
  const x = left ? 1 : 0, seam = left ? 'east' : 'west', latch = left ? 15 : 0;
  const model = new BlockModel(undefined, {chest: `minecraft:entity/chest/${texture}_${properties.type}`}, [
    box([x, 0, 1], [x + 15, 10, 15], [0, 19], seam),
    box([x, 9, 1], [x + 15, 14, 15], [0, 0], seam),
    box([latch, 7, 0], [latch + 1, 11, 1], [0, 0], seam),
  ]);
  const transform = mat4.create();
  mat4.scale(transform, transform, [1 / 16, 1 / 16, 1 / 16]);
  mat4.translate(transform, transform, [8, 8, 8]);
  mat4.rotateY(transform, transform, ({north: 0, west: 1, south: 2, east: 3}[properties.facing] ?? 0) * Math.PI / 2);
  mat4.translate(transform, transform, [-8, -8, -8]);
  return model.getMesh(atlas, Cull.none()).transform(transform);
}

export function chestDefinitions() {
  return Object.fromEntries([...textures.keys()].map(state => [chestRenderState(state, {type: 'left'}), {
    getMesh: (_name, properties, atlas) => doubleChestMesh(state, properties, atlas),
  }]));
}
