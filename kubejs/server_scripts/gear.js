// Harder tools and armour, three steps:
//  - iron: iron sheets instead of ingots (needs the mechanical press)
//  - diamond: the iron piece is upgraded with diamonds and a brass sheet (brass tier)
//  - netherite: the ingot needs a TFMG heavy plate (steel tier); smithing stays vanilla
// The same lists fill the incognita:gated_gear tags: the Incognita mod removes tagged gear from
// villager trades and chest loot, so there is no way around these recipes.

const VANILLA_SHAPES = {
  sword: ['M', 'M', 'S'],
  pickaxe: ['MMM', ' S ', ' S '],
  axe: ['MM', 'MS', ' S'],
  shovel: ['M', 'S', 'S'],
  hoe: ['MM', ' S', ' S'],
  helmet: ['MMM', 'M M'],
  chestplate: ['M M', 'MMM', 'MMM'],
  leggings: ['MMM', 'M M', 'M M'],
  boots: ['M M', 'M M']
}

// D = diamond, T = the iron piece, B = brass sheet. The chestplate takes 7 diamonds: 8 do not fit.
const UPGRADE_SHAPES = {
  sword: [' D ', ' D ', 'BT '],
  pickaxe: ['DDD', ' T ', ' B '],
  axe: ['DD ', 'DT ', ' B '],
  shovel: [' D ', ' T ', ' B '],
  hoe: ['DD ', ' T ', ' B '],
  helmet: ['DDD', 'DTD', ' B '],
  chestplate: ['DTD', 'DDD', 'DBD'],
  leggings: ['DDD', 'DTD', 'DB '],
  boots: ['DTD', 'DBD']
}

// Knives and fishing rods from other mods, same two steps
const EXTRA = {
  iron: ['farmersdelight:iron_knife', 'aquaculture:iron_fillet_knife', 'aquaculture:iron_fishing_rod'],
  diamond: ['farmersdelight:diamond_knife', 'aquaculture:diamond_fillet_knife', 'aquaculture:diamond_fishing_rod']
}

const parts = Object.keys(VANILLA_SHAPES)

ServerEvents.tags('item', event => {
  parts.forEach(part => {
    event.add('incognita:gated_gear/iron', 'minecraft:iron_' + part)
    event.add('incognita:gated_gear/diamond', 'minecraft:diamond_' + part)
    event.add('incognita:gated_gear/netherite', 'minecraft:netherite_' + part)
  })
  const present = list => list.filter(id => Item.exists(id))
  event.add('incognita:gated_gear/iron', present(EXTRA.iron))
  event.add('incognita:gated_gear/diamond', present(EXTRA.diamond))
  event.add('incognita:gated_gear/netherite', present(['farmersdelight:netherite_knife']))
})

ServerEvents.recipes(event => {
  const name = id => id.split(':')[1]
  // Declared here: Rhino fails on "const" inside an if block
  const scrap = 'minecraft:netherite_scrap'
  const gold = 'minecraft:gold_ingot'

  // --- Step 1: iron ---
  parts.forEach(part => {
    const id = 'minecraft:iron_' + part
    const key = { M: 'create:iron_sheet' }
    if (VANILLA_SHAPES[part].join('').indexOf('S') >= 0) key.S = '#c:rods/wooden'
    event.remove({ id: id })
    event.shaped(id, VANILLA_SHAPES[part], key).id('sborka:gear_iron_' + part)
  })
  EXTRA.iron.forEach(id => {
    if (!Item.exists(id)) return
    event.replaceInput({ id: id }, '#c:ingots/iron', 'create:iron_sheet')
  })

  // Shears join the iron step: two sheets and a plank handle (durability is raised in startup_scripts/durability.js)
  event.remove({ id: 'minecraft:shears' })
  event.shaped('minecraft:shears', [' M', 'MP'], { M: 'create:iron_sheet', P: '#minecraft:planks' })
    .id('sborka:gear_shears')

  // --- Step 2: diamond ---
  parts.forEach(part => {
    const id = 'minecraft:diamond_' + part
    event.remove({ id: id })
    event.shaped(id, UPGRADE_SHAPES[part], {
      D: '#c:gems/diamond', T: 'minecraft:iron_' + part, B: 'create:brass_sheet'
    }).id('sborka:gear_diamond_' + part)
  })
  EXTRA.diamond.forEach((id, n) => {
    if (!Item.exists(id)) return
    event.remove({ id: id })
    event.shapeless(id, [EXTRA.iron[n], '#c:gems/diamond', '#c:gems/diamond', 'create:brass_sheet'])
      .id('sborka:gear_' + name(id))
  })

  // --- Step 3: netherite ---
  if (Platform.isLoaded('tfmg')) {
    event.remove({ id: 'minecraft:netherite_ingot' })
    event.shapeless('minecraft:netherite_ingot',
      [scrap, scrap, scrap, scrap, gold, gold, gold, gold, 'tfmg:heavy_plate'])
      .id('sborka:gear_netherite_ingot')
  }
})
