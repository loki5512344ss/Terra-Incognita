// Immersive Engineering tier 6 (heavy industry). The excavator and arc furnace are multiblocks built
// with the hammer, so they have no crafting recipe here. The tesla coil keeps its own (late) recipe.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('immersiveengineering')) return

  // Turrets need TFMG steel and precision mechanisms instead of the IE revolver line
  event.remove({ output: 'immersiveengineering:turret_gun' })
  event.shaped('immersiveengineering:turret_gun', ['SES', 'GCG', 'S S'], {
    S: '#c:ingots/steel', E: 'create:electron_tube', G: 'create:precision_mechanism', C: 'create:brass_casing'
  }).id('sborka:ie_turret')
})
