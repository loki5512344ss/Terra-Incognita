// Terra Incognita recipes (KubeJS 7, MC 1.21.1). One file per topic; split further as it grows.
ServerEvents.recipes(event => {
  // --- Create: the mixer needs an iron sheet (the press is untouched because it makes the sheets) ---
  event.remove({ output: 'create:mechanical_mixer' })
  event.shaped('create:mechanical_mixer', [
    'SP',
    'C ',
    'W '
  ], {
    S: 'create:cogwheel',
    P: 'create:iron_sheet',
    C: 'create:andesite_casing',
    W: 'create:whisk'
  }).id('sborka:mechanical_mixer')

  // The compass and radio moved to radio_gates.js (they need sequenced-assembly parts)

  // --- World-center compass (our incognita mod): a plain compass wrapped in brass sheets, points to 0 0 ---
  event.shaped('incognita:origin_compass', [
    ' B ',
    'BCB',
    ' B '
  ], {
    B: 'create:brass_sheet',
    C: 'minecraft:compass'
  }).id('sborka:origin_compass')
})
