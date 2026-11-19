// One steel for the whole pack: only TFMG makes it (blast furnace -> melt -> casting mold).
// IE steel stays in the registry but is no longer craftable; every recipe that needs steel accepts the c:ingots/steel tag.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // IE steel ingot and dust and the IE blast furnaces (list in startup_scripts/removed.js)
  global.terraRemoved.ieSteel.forEach(id => event.remove({ output: id }))

  // IE recipes that asked for the IE ingot now accept any steel (that is, TFMG steel)
  event.replaceInput({ mod: 'immersiveengineering' }, 'immersiveengineering:ingot_steel', '#c:ingots/steel')
})
