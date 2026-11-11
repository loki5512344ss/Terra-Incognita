// Усложнение машин TFMG: к цепочке сталь/нефть добавлены наши промежуточные предметы
// (футеровка, термопара, манометр), которые собираются на Create sequenced assembly.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  const sa = event.recipes.create

  // --- Огнеупорная футеровка: латунь + огнеупорный кирпич + шлак ---
  sa.sequenced_assembly(['kubejs:refractory_lining'], 'create:brass_sheet', [
    sa.deploying('kubejs:incomplete_refractory_lining', ['kubejs:incomplete_refractory_lining', 'tfmg:fireproof_brick']),
    sa.filling('kubejs:incomplete_refractory_lining', ['kubejs:incomplete_refractory_lining', Fluid.of('tfmg:molten_slag', 90)]),
    sa.pressing('kubejs:incomplete_refractory_lining', 'kubejs:incomplete_refractory_lining')
  ]).transitionalItem('kubejs:incomplete_refractory_lining').loops(2).id('sborka:refractory_lining')

  // --- Термопара: железный лист + медная и константановая проволока ---
  sa.sequenced_assembly(['kubejs:thermocouple'], 'create:iron_sheet', [
    sa.deploying('kubejs:incomplete_thermocouple', ['kubejs:incomplete_thermocouple', 'tfmg:copper_wire']),
    sa.deploying('kubejs:incomplete_thermocouple', ['kubejs:incomplete_thermocouple', 'tfmg:constantan_wire']),
    sa.pressing('kubejs:incomplete_thermocouple', 'kubejs:incomplete_thermocouple')
  ]).transitionalItem('kubejs:incomplete_thermocouple').loops(1).id('sborka:thermocouple')

  // --- Манометр: латунный лист + стекло + электронная лампа ---
  sa.sequenced_assembly(['kubejs:pressure_gauge'], 'create:brass_sheet', [
    sa.deploying('kubejs:incomplete_pressure_gauge', ['kubejs:incomplete_pressure_gauge', 'minecraft:glass_pane']),
    sa.deploying('kubejs:incomplete_pressure_gauge', ['kubejs:incomplete_pressure_gauge', 'create:electron_tube']),
    sa.pressing('kubejs:incomplete_pressure_gauge', 'kubejs:incomplete_pressure_gauge')
  ]).transitionalItem('kubejs:incomplete_pressure_gauge').loops(1).id('sborka:pressure_gauge')

  // --- Гейты: переписываем крафты машин ---
  const redo = (id, output, count, pattern, key) => {
    event.remove({ id: 'tfmg:crafting/materials/' + id })
    event.shaped(Item.of(output, count), pattern, key).id('sborka:tfmg_' + id)
  }

  // Доменная печь и сталь
  redo('coke_oven', 'tfmg:coke_oven', 2, [' K ', 'CIC', ' C '], {
    K: 'kubejs:thermocouple', C: '#c:ingots/cast_iron', I: 'create:industrial_iron_block'
  })
  redo('blast_stove', 'tfmg:blast_stove', 2, ['CCK', 'TT ', 'FL '], {
    C: 'tfmg:cast_iron_pipe', T: 'tfmg:cast_iron_fluid_tank', F: 'tfmg:fireproof_bricks',
    L: 'kubejs:refractory_lining', K: 'kubejs:thermocouple'
  })
  redo('blast_furnace_output', 'tfmg:blast_furnace_output', 1, ['BCB', 'CQC', 'BLB'], {
    B: 'tfmg:fireproof_bricks', C: 'tfmg:cast_iron_pipe', Q: '#c:storage_blocks/cast_iron', L: 'kubejs:refractory_lining'
  })
  redo('blast_furnace_reinforcement', 'tfmg:blast_furnace_reinforcement', 4, ['IOB', 'IOL', 'IOB'], {
    I: '#c:ingots/steel', O: 'tfmg:heavy_plate', B: 'tfmg:fireproof_bricks', L: 'kubejs:refractory_lining'
  })
  redo('firebox', 'tfmg:firebox', 1, ['BTB', 'PKP', 'BLB'], {
    B: 'tfmg:fireproof_bricks', T: 'tfmg:steel_fluid_tank', P: 'tfmg:brass_pipe',
    K: 'kubejs:thermocouple', L: 'kubejs:refractory_lining'
  })

  // Нефть: добыча и перегонка требуют манометр
  redo('pumpjack_base', 'tfmg:pumpjack_base', 1, ['STG', 'MCM', 'OQO'], {
    S: '#c:strings', T: 'tfmg:steel_fluid_tank', G: 'kubejs:pressure_gauge',
    M: 'tfmg:steel_mechanism', C: 'tfmg:heavy_machinery_casing', O: 'tfmg:steel_pipe', Q: 'tfmg:industrial_pipe'
  })
  redo('steel_distillation_controller', 'tfmg:steel_distillation_controller', 1, ['SPG', 'ECE', 'MHM'], {
    S: '#c:plates/lead', P: 'tfmg:industrial_pipe', G: 'kubejs:pressure_gauge', E: 'create:electron_tube',
    C: 'minecraft:compass', M: 'tfmg:steel_mechanism', H: 'tfmg:heavy_machinery_casing'
  })
})
