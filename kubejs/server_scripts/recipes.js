// Рецепты Terra Incognita (KubeJS 7, MC 1.21.1). Один файл на тему — разносить по мере роста.
ServerEvents.recipes(event => {
  // --- Create: миксер требует железный лист (пресс не трогаем — лист делается на прессе) ---
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

  // --- Ваниль: компас требует точный механизм ---
  event.remove({ output: 'minecraft:compass' })
  event.shaped('minecraft:compass', [
    ' I ',
    'IPI',
    ' R '
  ], {
    I: 'create:iron_sheet',
    P: 'create:precision_mechanism',
    R: 'minecraft:redstone'
  }).id('sborka:compass')

  // --- Рация (наш мод incognita): точный механизм в центре, громоотвод-антенна, железные листы, редстоун ---
  event.shaped('incognita:radio', [
    ' L ',
    'SPS',
    ' R '
  ], {
    L: 'minecraft:lightning_rod',
    S: 'create:iron_sheet',
    P: 'create:precision_mechanism',
    R: 'minecraft:redstone'
  }).id('sborka:radio')

  // --- Компас центра мира (наш мод incognita): обычный компас в латунных листах, указывает на 0 0 ---
  event.shaped('incognita:origin_compass', [
    ' B ',
    'BCB',
    ' B '
  ], {
    B: 'create:brass_sheet',
    C: 'minecraft:compass'
  }).id('sborka:origin_compass')
})
