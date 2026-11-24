// Intermediate items of Terra Incognita. They gate machine recipes:
//  - TFMG machines (server_scripts/tfmg_gates.js)
//  - radio and compass (server_scripts/radio_gates.js)
//  - Create, electricity, trains, aviation, navigation (server_scripts/stage_gates.js)
// Display names live in assets/kubejs/lang; textures are drawn by local_script/gen-item-textures.py.
StartupEvents.registry('item', event => {
  // Parts built with Create sequenced assembly: each also needs an "incomplete" transitional item
  const assembled = ['refractory_lining', 'thermocouple', 'pressure_gauge', 'copper_coil',
    'quartz_oscillator', 'magnetized_needle', 'radio_tuner', 'optical_lens']
  assembled.forEach(id => {
    event.create(id)
    event.create('incomplete_' + id, 'create:sequenced_assembly').texture('kubejs:item/incomplete_' + id)
  })

  // Parts made in the crafting table
  event.create('signal_relay')
  event.create('propeller_hub')

  // Transitional items for the OC2R sequenced assemblies (transistors and circuit boards)
  ;['transistor', 'circuit_board'].forEach(id => {
    event.create('incomplete_' + id, 'create:sequenced_assembly').texture('kubejs:item/incomplete_' + id)
  })
})
