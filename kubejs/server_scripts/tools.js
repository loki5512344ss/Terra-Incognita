// Everyday items moved behind the same three steps as the gear (server_scripts/gear.js):
//  - press: iron sheets instead of ingots
//  - brass: golden and brass sheets
//  - steel: TFMG steel; the beacon also needs our optical lens
// The spyglass already needs the lens (server_scripts/stage_gates.js).
// Shield and shears durability is raised in startup_scripts/durability.js.

ServerEvents.recipes(event => {
  const redo = (item, pattern, key) => {
    const id = 'minecraft:' + item
    event.remove({ id: id })
    event.shaped(id, pattern, key).id('sborka:tool_' + item)
  }
  const iron = 'create:iron_sheet'
  const brass = 'create:brass_sheet'
  const planks = '#minecraft:planks'
  const obsidian = 'minecraft:obsidian'
  const diamond = '#c:gems/diamond'

  // --- Press ---
  redo('bucket', ['M M', ' M '], { M: iron })
  event.remove({ id: 'minecraft:flint_and_steel' })
  event.shapeless('minecraft:flint_and_steel', [iron, 'minecraft:flint']).id('sborka:tool_flint_and_steel')
  redo('shield', ['WMW', 'WWW', ' W '], { W: planks, M: iron })
  redo('hopper', ['M M', 'MCM', ' M '], { M: iron, C: '#c:chests/wooden' })
  redo('minecart', ['M M', 'MMM'], { M: iron })
  redo('crossbow', ['SMS', 'TKT', ' A '], {
    S: '#c:rods/wooden', M: iron, T: '#c:strings', K: 'minecraft:tripwire_hook', A: 'create:andesite_alloy'
  })

  // --- Brass ---
  redo('clock', [' G ', 'GCG', ' G '], { G: 'create:golden_sheet', C: 'create:cogwheel' })
  redo('enchanting_table', ['SBS', 'DOD', 'OOO'], {
    S: brass, B: 'minecraft:book', D: diamond, O: obsidian
  })
  redo('ender_chest', ['OBO', 'BEB', 'OBO'], { O: obsidian, B: brass, E: 'minecraft:ender_eye' })
  // Iron blocks as in vanilla, brass instead of plain iron blocks for the top and middle layers: costs more than
  // vanilla (4 iron blocks = 36 iron ingots) without waiting on TFMG steel. It also breaks about 5 times less often
  // than vanilla (gear/anvil/AnvilConfig.java in the incognita mod).
  redo('anvil', ['BBB', ' T ', 'TTT'], { B: brass, T: 'minecraft:iron_block' })

  // --- Steel ---
  if (Platform.isLoaded('tfmg')) {
    redo('beacon', ['GLG', 'TNT', 'OOO'], {
      G: 'minecraft:glass', L: 'kubejs:optical_lens', T: '#c:ingots/steel',
      N: 'minecraft:nether_star', O: obsidian
    })
  }
})
