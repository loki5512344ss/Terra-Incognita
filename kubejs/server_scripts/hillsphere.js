// Hill Sphere: the gravity core and goggles are late brass/mechanism items.
// The core needs our signal relay and precision mechanisms; the goggles use optical
// lenses instead of plain glass.
ServerEvents.recipes(event => {
  event.remove({ id: 'hillsphere:hill_core' })
  event.remove({ id: 'hillsphere:hill_goggles' })

  event.recipes.create.mechanical_crafting('hillsphere:hill_core', ['PMP', 'CRC', 'PMP'], {
    P: 'hillsphere:hill_plate',
    M: 'create:precision_mechanism',
    C: 'create:brass_casing',
    R: 'kubejs:signal_relay'
  }).id('sborka:hillsphere_hill_core')

  event.shaped('hillsphere:hill_goggles', ['BPB', 'L L'], {
    B: 'create:brass_sheet',
    P: 'hillsphere:gravite_sheet',
    L: 'kubejs:optical_lens'
  }).id('sborka:hillsphere_hill_goggles')
})
