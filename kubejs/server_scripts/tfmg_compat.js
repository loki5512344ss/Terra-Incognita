// IE and TFMG compatibility (this used to be the Alk1 datapack; we use no datapacks, everything is KubeJS).
ServerEvents.tags('fluid', event => {
  if (!Platform.isLoaded('tfmg')) return
  // The IE drill runs on TFMG diesel and gasoline
  event.add('immersiveengineering:drill_fuel', ['tfmg:diesel', 'tfmg:gasoline'])
})

ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return
  // Plastic sheet: pressed from the melt in a heated basin (Create compacting) instead of casting
  event.remove({ id: 'tfmg:casting/plastic_sheet' })
  event.recipes.create.compacting('tfmg:plastic_sheet', Fluid.of('tfmg:molten_plastic', 250))
    .heated().id('sborka:plastic_sheet')
})
