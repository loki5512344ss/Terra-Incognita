// Decor and building: Chipped, Rechiseled, Macaw's Roofs/Trapdoors.
// Gated by tier: basic decor at andesite/copper, premium at brass.
ServerEvents.recipes(event => {
  // Declared at the top of the callback: Rhino fails on "const" inside an if block
  const redo = (output, count, pattern, key, id) => {
    event.remove({ output: output })
    event.shaped(Item.of(output, count), pattern, key).id('sborka:' + id)
  }

  // --- Chipped workbenches (tier 2, copper): each needs a crafting table + iron sheet ---
  if (Platform.isLoaded('chipped')) {
    [
      ['mason_table', 'minecraft:stone'],
      ['carpenters_table', '#minecraft:planks'],
      ['glassblower', '#c:glass_blocks'],
      ['loom_table', '#minecraft:wool'],
      ['alchemy_bench', 'minecraft:brewing_stand'],
      ['tinkering_table', 'minecraft:piston'],
      ['botanist_workbench', 'minecraft:flower_pot']
    ].forEach(entry => {
      event.remove({ output: 'chipped:' + entry[0] })
      event.shapeless('chipped:' + entry[0], ['minecraft:crafting_table', 'create:iron_sheet', entry[1]])
        .id('sborka:chipped_' + entry[0])
    })
    // Chisel tool: iron sheet + stick
    redo('chipped:chisel', 1, ['I', 'S'], { I: 'create:iron_sheet', S: '#c:rods/wooden' }, 'chipped_chisel')
  }

  // --- Rechiseled chisel (tier 2): iron sheet + stick, same idea ---
  if (Platform.isLoaded('rechiseled')) {
    redo('rechiseled:chisel', 1, [' I', 'S '], {
      I: 'create:iron_sheet', S: '#c:rods/wooden'
    }, 'rechiseled_chisel')
  }

  // --- Macaw's Roofs (tier 2-3): roofing hammer needs brass (tier 3) ---
  if (Platform.isLoaded('mcwroofs')) {
    redo('mcwroofs:roofing_hammer', 1, ['BB ', ' SI', 'S  '], {
      B: 'create:brass_sheet', S: '#c:rods/wooden', I: 'create:iron_sheet'
    }, 'mcwroofs_hammer')
  }

  // --- Macaw's Trapdoors (tier 1): just planks, but iron trapdoor variant needs iron sheet ---
  if (Platform.isLoaded('mcwtrpdoors')) {
    event.replaceInput({ mod: 'mcwtrpdoors' }, 'minecraft:iron_ingot', 'create:iron_sheet')
  }
})
