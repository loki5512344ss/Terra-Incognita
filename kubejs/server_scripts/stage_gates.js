// Progression gates for the stages around the TFMG chain:
//  1. brass-tier Create machines   2. electricity (Create Additions)
//  3. trains                       4. aviation and cannons
//  5. navigation (lens + magnetized needle)
// Parts come from startup_scripts/items.js and radio_gates.js. Every recipe here replaces the original one.
ServerEvents.recipes(event => {
  const sa = event.recipes.create
  const redo = (output, count, pattern, key, id) => {
    event.remove({ output: output })
    event.shaped(Item.of(output, count), pattern, key).id('sborka:' + id)
  }

  // --- Parts ---
  // Optical lens: glass pane + polished rose quartz, pressed
  sa.sequenced_assembly(['kubejs:optical_lens'], 'minecraft:glass_pane', [
    sa.deploying('kubejs:incomplete_optical_lens', ['kubejs:incomplete_optical_lens', 'create:polished_rose_quartz']),
    sa.pressing('kubejs:incomplete_optical_lens', 'kubejs:incomplete_optical_lens')
  ]).transitionalItem('kubejs:incomplete_optical_lens').loops(1).id('sborka:optical_lens')

  // Signal relay: coil + electron tube on a brass/iron plate
  event.shaped(Item.of('kubejs:signal_relay', 2), ['BEB', 'ICI'], {
    B: 'create:brass_sheet', E: 'create:electron_tube', I: 'create:iron_sheet', C: 'kubejs:copper_coil'
  }).id('sborka:signal_relay')

  // Propeller hub: shaft in a brass cross
  event.shaped('kubejs:propeller_hub', [' B ', 'ISI', ' B '], {
    B: 'create:brass_sheet', I: 'create:iron_sheet', S: 'create:shaft'
  }).id('sborka:propeller_hub')

  // --- 1. Brass tier ---
  // The deployer must NOT need a precision mechanism: the mechanism itself is assembled with deployers.
  redo('create:deployer', 1, ['H', 'C', 'P'], {
    H: 'create:electron_tube', C: 'create:brass_casing', P: 'create:brass_hand'
  }, 'deployer')
  redo('create:mechanical_crafter', 3, ['T', 'B', 'P'], {
    T: 'minecraft:crafting_table', B: 'create:brass_casing', P: 'create:precision_mechanism'
  }, 'mechanical_crafter')
  // The encased fan stays vanilla: washing and smelting are needed long before brass.

  // --- 2. Electricity: coils and an oscillator make motors and capacitors ---
  if (Platform.isLoaded('createaddition')) {
    redo('createaddition:electric_motor', 1, ['ICI', 'CBC', 'ISI'], {
      I: 'create:iron_sheet', C: 'kubejs:copper_coil', B: 'create:brass_casing', S: 'create:shaft'
    }, 'electric_motor')
    redo('createaddition:alternator', 1, ['ICI', 'CBC', 'IQI'], {
      I: 'create:iron_sheet', C: 'kubejs:copper_coil', B: 'create:brass_casing', Q: 'kubejs:quartz_oscillator'
    }, 'alternator')
    redo('createaddition:capacitor', 1, ['ICI', 'CQC', 'IRI'], {
      I: 'create:iron_sheet', C: 'kubejs:copper_coil', Q: 'kubejs:quartz_oscillator', R: 'minecraft:redstone'
    }, 'capacitor')
    redo('createaddition:tesla_coil', 1, ['CRC', 'CQC', 'IBI'], {
      C: 'kubejs:copper_coil', R: 'minecraft:redstone', Q: 'kubejs:quartz_oscillator',
      I: 'create:iron_sheet', B: 'create:brass_casing'
    }, 'tesla_coil')
  }

  // --- 3. Trains: signals and observers need a signal relay ---
  event.remove({ output: 'create:track_signal' })
  event.shapeless(Item.of('create:track_signal', 2), ['create:railway_casing', 'kubejs:signal_relay'])
    .id('sborka:track_signal')
  event.remove({ output: 'create:track_observer' })
  event.shapeless(Item.of('create:track_observer', 2), ['create:railway_casing', 'kubejs:signal_relay'])
    .id('sborka:track_observer')

  // --- 4. Aviation and cannons ---
  // Propeller bearings take a hub instead of a bare iron plate
  event.replaceInput({ id: 'aeronautics:propeller_bearing' }, '#c:plates/iron', 'kubejs:propeller_hub')
  event.replaceInput({ id: 'aeronautics:gyroscopic_propeller_bearing' }, '#minecraft:wooden_slabs', 'kubejs:propeller_hub')
  event.replaceInput({ id: 'aeronautics:smart_propeller' }, 'create:propeller', 'kubejs:propeller_hub')

  // Cannons need steel (TFMG only) and a pressure gauge
  if (Platform.isLoaded('createbigcannons')) {
    redo('createbigcannons:cannon_builder', 1, ['SGS', 'IMI', 'SBS'], {
      S: '#c:ingots/steel', G: 'kubejs:pressure_gauge', I: 'create:iron_sheet',
      M: 'create:precision_mechanism', B: 'create:brass_casing'
    }, 'cannon_builder')
  }

  // --- 5. Navigation: lens and magnetized needle ---
  redo('minecraft:spyglass', 1, ['L', 'C', 'C'], {
    L: 'kubejs:optical_lens', C: 'create:copper_sheet'
  }, 'spyglass')
  if (Platform.isLoaded('create_radar')) {
    redo('create_radar:binoculars', 1, ['L L', 'BCB'], {
      L: 'kubejs:optical_lens', B: 'create:brass_sheet', C: 'create:brass_casing'
    }, 'binoculars')
  }
  if (Platform.isLoaded('simulated')) {
    redo('simulated:navigation_table', 1, ['SLS', 'NPN', ' B '], {
      S: 'create:brass_sheet', L: 'kubejs:optical_lens', N: 'kubejs:magnetized_needle',
      P: 'create:precision_mechanism', B: 'create:brass_casing'
    }, 'navigation_table')
  }
})
