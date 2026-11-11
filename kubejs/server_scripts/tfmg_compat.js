// Совместимость IE и TFMG (раньше был датапак Alk1; датапаки не используем, всё на KubeJS).
ServerEvents.tags('fluid', event => {
  if (!Platform.isLoaded('tfmg')) return
  // Дрель IE работает на дизеле и бензине TFMG
  event.add('immersiveengineering:drill_fuel', ['tfmg:diesel', 'tfmg:gasoline'])
})

ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return
  // Пластиковый лист: вместо литья — прессование расплава в нагретом тазу (Create compacting)
  event.remove({ id: 'tfmg:casting/plastic_sheet' })
  event.recipes.create.compacting('tfmg:plastic_sheet', Fluid.of('tfmg:molten_plastic', 250))
    .heated().id('sborka:plastic_sheet')
})
