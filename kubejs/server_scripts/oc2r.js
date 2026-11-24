// OC2R rework: computers are late-game machinery, not a crafting-table detour.
//
// Tier 4 (brass + silicon): sand and quartz -> silicon -> wafers; transistors and circuit boards
// are now sequenced assemblies. Every cable, card and upgrade consumes real electronics.
// Tier 5 (precision + steel): CPUs, GPUs and storage need a mechanical crafter, the previous tier
// and precision mechanisms. Computers, robots and GPS need steel and a mechanical crafter.
// Tier 6 (oil + end): plastic boards, ender parts and internet blocks close the branch.
//
// Wrench transformations (flash_memory_custom, onyxos drives, manual) stay as OC2R ships them.
ServerEvents.recipes(event => {
  const replaced = [
    'block_operations_module', 'bus_cable', 'bus_interface', 'charger', 'circuit_board', 'computer',
    'cpu_tier_1', 'cpu_tier_2', 'cpu_tier_3', 'cpu_tier_4', 'crafting_module', 'environment_sensor',
    'file_import_export_card', 'flash_memory', 'flash_memory_medium', 'flash_memory_small', 'floppy',
    'floppy_modern', 'gps_tower', 'gpu_tier_1', 'gpu_tier_2', 'gpu_tier_3', 'gpu_tier_4',
    'hard_drive_extra_large', 'hard_drive_large', 'hard_drive_medium', 'hard_drive_small',
    'internet_card', 'internet_gateway', 'inventory_operations_module', 'keyboard', 'machine_interface',
    'media_station', 'memory_extra_large', 'memory_large', 'memory_medium', 'memory_small',
    'network_cable', 'network_connector', 'network_hub', 'network_interface_card', 'network_switch',
    'network_tunnel_card', 'network_tunnel_module', 'pci_card_cage', 'projector', 'raw_silicon_wafer',
    'redstone_interface', 'redstone_interface_card', 'robot', 'scanner_module', 'silicon', 'silicon_blend',
    'silicon_wafer', 'sound_card', 'tablet', 'transistor', 'vxlan_hub', 'wireless_access_point'
  ]
  replaced.forEach(id => event.remove({ id: 'oc2r:' + id }))

  const sa = event.recipes.create
  const craft = (out, pattern, key, id) => sa.mechanical_crafting(out, pattern, key).id('sborka:oc2r_' + id)
  const shaped = (out, pattern, key, id) => event.shaped(out, pattern, key).id('sborka:oc2r_' + id)

  // --- Silicon: heated mixing, blasting and pressing instead of a free crafting recipe ---
  sa.mixing('2x oc2r:silicon_blend', ['3x #c:sands', '2x #c:gems/quartz']).heated()
    .id('sborka:oc2r_silicon_blend')
  event.blasting('oc2r:silicon', 'oc2r:silicon_blend').cookingTime(300).xp(0.2)
    .id('sborka:oc2r_silicon')
  sa.pressing('oc2r:raw_silicon_wafer', 'oc2r:silicon').id('sborka:oc2r_raw_silicon_wafer')
  event.blasting('oc2r:silicon_wafer', 'oc2r:raw_silicon_wafer').cookingTime(300).xp(0.2)
    .id('sborka:oc2r_silicon_wafer')

  // --- Transistor: iron sheet, redstone and a whole silicon wafer, deployed and pressed ---
  sa.sequenced_assembly([Item.of('oc2r:transistor', 4)], 'create:iron_sheet', [
    sa.deploying('kubejs:incomplete_transistor', ['kubejs:incomplete_transistor', 'minecraft:redstone']),
    sa.deploying('kubejs:incomplete_transistor', ['kubejs:incomplete_transistor', 'oc2r:silicon_wafer']),
    sa.pressing('kubejs:incomplete_transistor', 'kubejs:incomplete_transistor')
  ]).transitionalItem('kubejs:incomplete_transistor').loops(1).id('sborka:oc2r_transistor')

  // --- Circuit board: brass substrate, gold foil and a transistor ---
  sa.sequenced_assembly(['oc2r:circuit_board'], 'create:brass_sheet', [
    sa.deploying('kubejs:incomplete_circuit_board', ['kubejs:incomplete_circuit_board', 'create:golden_sheet']),
    sa.deploying('kubejs:incomplete_circuit_board', ['kubejs:incomplete_circuit_board', 'oc2r:transistor']),
    sa.pressing('kubejs:incomplete_circuit_board', 'kubejs:incomplete_circuit_board')
  ]).transitionalItem('kubejs:incomplete_circuit_board').loops(1).id('sborka:oc2r_circuit_board')

  // --- Cables and bus interfaces ---
  shaped('4x oc2r:bus_cable', ['ICI', 'GTG', 'ICI'], {
    I: 'create:iron_sheet', C: 'tfmg:copper_wire', G: 'minecraft:gold_nugget', T: 'oc2r:transistor'
  }, 'bus_cable')
  event.shapeless('oc2r:bus_interface', ['oc2r:bus_cable', 'oc2r:circuit_board', 'create:brass_sheet', 'oc2r:transistor'])
    .id('sborka:oc2r_bus_interface')
  shaped('4x oc2r:network_cable', ['SGS', 'CTC', 'SGS'], {
    S: '#c:strings', G: '#c:glass_blocks', C: 'tfmg:copper_wire', T: 'oc2r:transistor'
  }, 'network_cable')
  shaped('2x oc2r:network_connector', ['IGI', 'CTC'], {
    I: 'create:iron_sheet', G: '#c:glass_blocks', C: 'tfmg:copper_wire', T: 'oc2r:transistor'
  }, 'network_connector')

  // --- Processors: mechanical crafter, each tier eats the previous one ---
  craft('oc2r:cpu_tier_1', ['ISI', 'TCT', 'BGB'], {
    I: 'create:iron_sheet', S: 'oc2r:silicon_wafer', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', G: 'create:golden_sheet'
  }, 'cpu_tier_1')
  craft('oc2r:cpu_tier_2', ['GSG', 'TCT', 'BQB'], {
    G: 'create:golden_sheet', S: 'oc2r:cpu_tier_1', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', Q: 'kubejs:quartz_oscillator'
  }, 'cpu_tier_2')
  craft('oc2r:cpu_tier_3', ['DSD', 'TCT', 'BQB'], {
    D: '#c:gems/diamond', S: 'oc2r:cpu_tier_2', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', Q: 'kubejs:signal_relay'
  }, 'cpu_tier_3')
  craft('oc2r:cpu_tier_4', ['ESE', 'TCT', 'BPB'], {
    E: '#c:gems/emerald', S: 'oc2r:cpu_tier_3', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', P: 'create:precision_mechanism'
  }, 'cpu_tier_4')

  // --- Graphics: lenses, then diamonds and precision ---
  craft('oc2r:gpu_tier_1', ['LIL', 'TCT', 'BGB'], {
    L: 'kubejs:optical_lens', I: 'create:iron_sheet', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', G: 'create:golden_sheet'
  }, 'gpu_tier_1')
  craft('oc2r:gpu_tier_2', ['LSL', 'TCT', 'BGB'], {
    L: 'kubejs:optical_lens', S: 'oc2r:gpu_tier_1', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', G: 'create:golden_sheet'
  }, 'gpu_tier_2')
  craft('oc2r:gpu_tier_3', ['DSD', 'TCT', 'BQB'], {
    D: '#c:gems/diamond', S: 'oc2r:gpu_tier_2', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', Q: 'kubejs:signal_relay'
  }, 'gpu_tier_3')
  craft('oc2r:gpu_tier_4', ['ESE', 'TCT', 'BPB'], {
    E: '#c:gems/emerald', S: 'oc2r:gpu_tier_3', T: 'oc2r:transistor', C: 'create:copper_sheet',
    B: 'oc2r:circuit_board', P: 'create:precision_mechanism'
  }, 'gpu_tier_4')

  // --- Memory: wafers, gold and the previous module ---
  shaped('2x oc2r:memory_small', ['ITI', 'WBW', 'RGR'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    R: 'minecraft:redstone', G: 'minecraft:gold_nugget'
  }, 'memory_small')
  shaped('2x oc2r:memory_medium', ['GTG', 'WBW', 'RSR'], {
    G: 'create:golden_sheet', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    R: 'minecraft:redstone', S: 'oc2r:memory_small'
  }, 'memory_medium')
  shaped('2x oc2r:memory_large', ['DTD', 'WBW', 'RSR'], {
    D: '#c:gems/diamond', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    R: 'minecraft:redstone', S: 'oc2r:memory_medium'
  }, 'memory_large')
  shaped('2x oc2r:memory_extra_large', ['DTD', 'WBW', 'ESE'], {
    D: '#c:gems/diamond', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    E: '#c:gems/emerald', S: 'oc2r:memory_large'
  }, 'memory_extra_large')

  // --- Drives: magnetized needles and emeralds ---
  shaped('oc2r:hard_drive_small', ['ITI', 'EBE', 'MNM'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', E: '#c:gems/emerald', B: 'oc2r:circuit_board',
    M: 'kubejs:magnetized_needle', N: 'minecraft:iron_ingot'
  }, 'hard_drive_small')
  shaped('oc2r:hard_drive_medium', ['GTG', 'EBE', 'MSM'], {
    G: 'create:golden_sheet', T: 'oc2r:transistor', E: '#c:gems/emerald', B: 'oc2r:circuit_board',
    M: 'kubejs:magnetized_needle', S: 'oc2r:hard_drive_small'
  }, 'hard_drive_medium')
  shaped('oc2r:hard_drive_large', ['DTD', 'EBE', 'MSM'], {
    D: '#c:gems/diamond', T: 'oc2r:transistor', E: '#c:gems/emerald', B: 'oc2r:circuit_board',
    M: 'kubejs:magnetized_needle', S: 'oc2r:hard_drive_medium'
  }, 'hard_drive_large')
  shaped('oc2r:hard_drive_extra_large', ['DTD', 'ESE', 'MNM'], {
    D: '#c:gems/diamond', T: 'oc2r:transistor', E: '#c:gems/emerald', S: 'oc2r:hard_drive_large',
    M: 'kubejs:magnetized_needle', N: 'minecraft:iron_ingot'
  }, 'hard_drive_extra_large')

  // --- Flash memory and floppies ---
  shaped('oc2r:flash_memory_small', ['ITI', 'RBR'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', R: 'minecraft:redstone', B: 'oc2r:circuit_board'
  }, 'flash_memory_small')
  shaped('oc2r:flash_memory', ['ITI', 'WBW', 'RGR'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    R: 'minecraft:redstone', G: 'minecraft:gold_nugget'
  }, 'flash_memory')
  shaped('oc2r:flash_memory_medium', ['ITI', 'WBW', 'RSR'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', W: 'oc2r:silicon_wafer', B: 'oc2r:circuit_board',
    R: 'minecraft:redstone', S: 'oc2r:flash_memory'
  }, 'flash_memory_medium')
  shaped('oc2r:floppy', ['ITI', 'QBQ', ' M '], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', Q: '#c:gems/quartz', B: 'oc2r:circuit_board',
    M: 'kubejs:magnetized_needle'
  }, 'floppy')
  shaped('oc2r:floppy_modern', ['ITI', 'QBQ', 'MSM'], {
    I: 'create:iron_sheet', T: 'oc2r:transistor', Q: '#c:gems/quartz', B: 'oc2r:circuit_board',
    M: 'kubejs:magnetized_needle', S: 'oc2r:floppy'
  }, 'floppy_modern')

  // --- Peripherals ---
  shaped('oc2r:keyboard', ['UUU', 'BTB', 'ICI'], {
    U: '#minecraft:buttons', B: 'create:brass_sheet', T: 'oc2r:transistor', I: 'create:iron_sheet',
    C: 'oc2r:circuit_board'
  }, 'keyboard')
  shaped('oc2r:pci_card_cage', ['I I', 'BTB', 'ICI'], {
    I: 'create:iron_sheet', B: 'create:brass_sheet', T: 'oc2r:transistor', C: 'oc2r:circuit_board'
  }, 'pci_card_cage')
  shaped('oc2r:charger', ['ICI', 'BTB', 'XRX'], {
    I: 'create:iron_sheet', C: 'kubejs:copper_coil', B: 'create:brass_casing', T: 'oc2r:transistor',
    X: 'oc2r:bus_interface', R: '#c:storage_blocks/redstone'
  }, 'charger')
  shaped('oc2r:media_station', ['ICI', 'DFD', 'XBX'], {
    I: 'create:iron_sheet', C: 'oc2r:circuit_board', D: 'minecraft:redstone', F: 'minecraft:comparator',
    X: 'oc2r:bus_interface', B: 'create:brass_casing'
  }, 'media_station')
  shaped('oc2r:redstone_interface', ['ICI', 'XTX', 'IBI'], {
    I: 'create:iron_sheet', C: 'kubejs:signal_relay', X: 'oc2r:bus_interface', T: 'oc2r:transistor',
    B: 'oc2r:circuit_board'
  }, 'redstone_interface')
  shaped('oc2r:machine_interface', ['IRI', 'HCH', 'IBX'], {
    I: '#c:ingots/steel', R: 'minecraft:comparator', H: 'minecraft:hopper', C: 'oc2r:circuit_board',
    B: 'create:brass_casing', X: 'oc2r:bus_interface'
  }, 'machine_interface')
  shaped('oc2r:environment_sensor', ['IGI', 'DBD', 'XLX'], {
    I: 'create:iron_sheet', G: '#c:glass_blocks', D: 'minecraft:daylight_detector', B: 'oc2r:circuit_board',
    X: 'oc2r:bus_interface', L: 'kubejs:optical_lens'
  }, 'environment_sensor')
  shaped('oc2r:projector', ['GLG', 'LTD', 'GBG'], {
    G: 'create:golden_sheet', L: 'kubejs:optical_lens', T: 'oc2r:transistor', D: '#c:gems/diamond',
    B: 'oc2r:circuit_board'
  }, 'projector')

  // --- Cards and modules ---
  shaped('oc2r:sound_card', ['IST', ' B '], {
    I: 'create:iron_sheet', S: 'minecraft:note_block', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'sound_card')
  shaped('oc2r:redstone_interface_card', ['IRT', ' B '], {
    I: 'create:iron_sheet', R: 'minecraft:redstone_torch', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'redstone_interface_card')
  shaped('oc2r:network_interface_card', ['IGT', ' B '], {
    I: 'create:iron_sheet', G: '#c:glass_blocks', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'network_interface_card')
  shaped('oc2r:network_tunnel_card', ['IET', ' B '], {
    I: 'create:iron_sheet', E: '#c:ender_pearls', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'network_tunnel_card')
  shaped('oc2r:network_tunnel_module', ['TEG', ' B '], {
    T: 'oc2r:transistor', E: '#c:ender_pearls', G: 'minecraft:gold_ingot', B: 'oc2r:circuit_board'
  }, 'network_tunnel_module')
  shaped('oc2r:file_import_export_card', ['IET', ' B '], {
    I: 'create:iron_sheet', E: 'minecraft:paper', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'file_import_export_card')
  shaped('oc2r:block_operations_module', ['TPG', 'RBR'], {
    T: 'oc2r:transistor', P: 'minecraft:diamond_pickaxe', G: 'minecraft:gold_ingot',
    R: 'kubejs:signal_relay', B: 'oc2r:circuit_board'
  }, 'block_operations_module')
  shaped('oc2r:inventory_operations_module', ['TCG', 'RBR'], {
    T: 'oc2r:transistor', C: 'minecraft:chest', G: 'minecraft:gold_ingot',
    R: 'kubejs:signal_relay', B: 'oc2r:circuit_board'
  }, 'inventory_operations_module')
  shaped('oc2r:crafting_module', ['TRC', 'SBS'], {
    T: 'oc2r:transistor', R: 'minecraft:crafting_table', C: 'create:copper_sheet',
    S: 'kubejs:signal_relay', B: 'oc2r:circuit_board'
  }, 'crafting_module')
  shaped('oc2r:scanner_module', ['LSC', 'TBT'], {
    L: 'kubejs:optical_lens', S: 'minecraft:spyglass', C: 'create:copper_sheet',
    T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'scanner_module')

  // --- Network blocks ---
  shaped('oc2r:network_hub', ['ICI', 'XTX', 'IBI'], {
    I: 'create:iron_sheet', C: 'oc2r:network_connector', X: 'oc2r:bus_interface',
    T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'network_hub')
  shaped('oc2r:network_switch', ['ICI', 'TBT', 'ICI'], {
    I: 'create:iron_sheet', C: 'oc2r:network_connector', T: 'oc2r:transistor', B: 'oc2r:circuit_board'
  }, 'network_switch')
  shaped('oc2r:vxlan_hub', ['NCN', 'XTX', 'IBX'], {
    N: 'oc2r:network_connector', C: 'oc2r:network_cable', X: 'oc2r:bus_interface',
    T: 'oc2r:transistor', I: 'create:iron_sheet', B: 'oc2r:circuit_board'
  }, 'vxlan_hub')
  shaped('oc2r:wireless_access_point', [' E ', 'IBI', ' N '], {
    E: '#c:ender_pearls', I: 'create:iron_sheet', B: 'oc2r:circuit_board', N: 'oc2r:network_connector'
  }, 'wireless_access_point')
  shaped('oc2r:internet_gateway', ['IEI', 'XTX', 'IBI'], {
    I: '#c:ingots/steel', E: '#c:ender_pearls', X: 'oc2r:bus_interface', T: 'oc2r:transistor',
    B: 'oc2r:circuit_board'
  }, 'internet_gateway')
  shaped('oc2r:internet_card', ['IEI', 'XTX', 'IBI'], {
    I: 'create:iron_sheet', E: 'minecraft:ender_eye', X: 'oc2r:bus_interface', T: 'oc2r:transistor',
    B: 'oc2r:circuit_board'
  }, 'internet_card')

  // --- Computer, tablet and robot: steel frames and precision mechanisms ---
  event.custom({
    type: 'minecraft:crafting_shaped',
    pattern: ['BSB', 'XCX', 'XMX'],
    key: {
      B: { item: 'create:brass_casing' },
      S: { tag: 'c:ingots/steel' },
      X: { item: 'oc2r:bus_interface' },
      C: { item: 'oc2r:circuit_board' },
      M: { item: 'create:precision_mechanism' }
    },
    result: {
      id: 'oc2r:computer',
      count: 1,
      components: {
        'oc2r:restricted_container': {
          items: {
            'oc2r:devices/card': [{}, {}, {}, {}],
            'oc2r:devices/cpu': [{}],
            'oc2r:devices/flash_memory': [{ count: 1, id: 'oc2r:flash_memory_custom' }],
            'oc2r:devices/hard_drive': [{}, {}, {}, {}],
            'oc2r:devices/memory': [{}, {}, {}, {}]
          }
        }
      }
    }
  }).id('sborka:oc2r_computer')

  shaped('oc2r:tablet', ['IGI', 'BCT', 'IMI'], {
    I: '#c:ingots/steel', G: 'minecraft:glass_pane', B: 'create:brass_casing', C: 'oc2r:circuit_board',
    T: 'oc2r:transistor', M: 'create:precision_mechanism'
  }, 'tablet')

  event.custom({
    type: 'minecraft:crafting_shaped',
    pattern: ['ISI', 'PCP', 'XMX'],
    key: {
      I: { tag: 'c:ingots/steel' },
      S: { item: 'tfmg:steel_casing' },
      P: { item: 'minecraft:piston' },
      C: { item: 'oc2r:circuit_board' },
      X: { item: 'oc2r:bus_interface' },
      M: { item: 'create:precision_mechanism' }
    },
    result: {
      id: 'oc2r:robot',
      count: 1,
      components: {
        'oc2r:restricted_container': {
          items: {
            'oc2r:devices/cpu': [{}],
            'oc2r:devices/flash_memory': [{ count: 1, id: 'oc2r:flash_memory_custom' }],
            'oc2r:devices/hard_drive': [{}, {}],
            'oc2r:devices/memory': [{}, {}, {}, {}],
            'oc2r:devices/robot_module': [{}, {}, {}, {}]
          }
        }
      }
    }
  }).id('sborka:oc2r_robot')

  // --- GPS: steel, a tier-3 CPU and a signal relay ---
  craft('oc2r:gps_tower', ['ISI', 'RCR', 'XMX'], {
    I: '#c:ingots/steel', S: 'oc2r:cpu_tier_3', R: 'kubejs:signal_relay', C: 'oc2r:circuit_board',
    X: 'oc2r:bus_interface', M: 'create:precision_mechanism'
  }, 'gps_tower')
})
