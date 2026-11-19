// One oil: extraction and refining come only from TFMG (pumpjack -> distillation -> diesel/gasoline/kerosene).
//  - IE oil machines are removed. Immersive Petroleum is cut from the pack entirely (jar disabled).
//  - Create Diesel Generators stays for its engines: they burn the c:diesel / c:gasoline tags,
//    which TFMG already fills with its fluids, so the engines run on TFMG fuel without changes.
//    CDG loses its own oil extraction and refining; biofuel (ethanol, plant oil, biodiesel) stays.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // Machines from startup_scripts/removed.js: IE oil and CDG extraction/refining (TFMG has its own)
  global.terraRemoved.ieOil.concat(global.terraRemoved.cdgOil).forEach(id => event.remove({ output: id }))
  event.remove({ type: 'createdieselgenerators:distillation' })
})

// Disable CDG oil deposits in the world with an empty biome tag (TFMG generates the oil)
ServerEvents.tags('worldgen/biome', event => {
  if (!Platform.isLoaded('tfmg')) return
  event.removeAll('createdieselgenerators:oil_biomes')
})
