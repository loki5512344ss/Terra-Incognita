// Items that now cost iron sheets (server_scripts/gear.js, tools.js) last longer than vanilla ones.
ItemEvents.modification(event => {
  const durability = {
    'minecraft:shears': 400, // vanilla 238
    'minecraft:shield': 500 // vanilla 336
  }
  Object.keys(durability).forEach(id => {
    event.modify(id, item => {
      item.maxDamage = durability[id]
    })
  })
})
