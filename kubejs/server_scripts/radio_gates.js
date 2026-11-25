// Communication and navigation: radio and compass parts are built with Create sequenced assembly.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return
  const sa = event.recipes.create

  // Copper coil: copper sheet + two turns of wire
  sa.sequenced_assembly(['kubejs:copper_coil'], 'create:copper_sheet', [
    sa.deploying('kubejs:incomplete_copper_coil', ['kubejs:incomplete_copper_coil', 'tfmg:copper_wire']),
    sa.pressing('kubejs:incomplete_copper_coil', 'kubejs:incomplete_copper_coil')
  ]).transitionalItem('kubejs:incomplete_copper_coil').loops(2).id('sborka:copper_coil')

  // Quartz oscillator: gold sheet + polished rose quartz
  sa.sequenced_assembly(['kubejs:quartz_oscillator'], 'create:golden_sheet', [
    sa.deploying('kubejs:incomplete_quartz_oscillator', ['kubejs:incomplete_quartz_oscillator', 'create:polished_rose_quartz']),
    sa.pressing('kubejs:incomplete_quartz_oscillator', 'kubejs:incomplete_quartz_oscillator')
  ]).transitionalItem('kubejs:incomplete_quartz_oscillator').loops(1).id('sborka:quartz_oscillator')

  // Magnetized needle: iron nugget + a coil (magnetizes it), then pressed
  sa.sequenced_assembly(['kubejs:magnetized_needle'], 'minecraft:iron_nugget', [
    sa.deploying('kubejs:incomplete_magnetized_needle', ['kubejs:incomplete_magnetized_needle', 'kubejs:copper_coil']),
    sa.pressing('kubejs:incomplete_magnetized_needle', 'kubejs:incomplete_magnetized_needle')
  ]).transitionalItem('kubejs:incomplete_magnetized_needle').loops(1).id('sborka:magnetized_needle')

  // Radio tuner: brass plate + coil + oscillator + electron tube
  sa.sequenced_assembly(['kubejs:radio_tuner'], 'create:brass_sheet', [
    sa.deploying('kubejs:incomplete_radio_tuner', ['kubejs:incomplete_radio_tuner', 'kubejs:copper_coil']),
    sa.deploying('kubejs:incomplete_radio_tuner', ['kubejs:incomplete_radio_tuner', 'kubejs:quartz_oscillator']),
    sa.deploying('kubejs:incomplete_radio_tuner', ['kubejs:incomplete_radio_tuner', 'create:electron_tube']),
    sa.pressing('kubejs:incomplete_radio_tuner', 'kubejs:incomplete_radio_tuner')
  ]).transitionalItem('kubejs:incomplete_radio_tuner').loops(1).id('sborka:radio_tuner')

  // Radio: the radio tuner replaces the redstone at the bottom
  event.remove({ id: 'incognita:radio' })
  event.shaped('incognita:radio', [' L ', 'SPS', ' T '], {
    L: 'minecraft:lightning_rod', S: 'create:iron_sheet',
    P: 'create:precision_mechanism', T: 'kubejs:radio_tuner'
  }).id('sborka:radio')

  // Compass: a magnetized needle replaces the redstone
  event.remove({ id: 'minecraft:compass' })
  event.shaped('minecraft:compass', [' I ', 'IPI', ' N '], {
    I: 'create:iron_sheet', P: 'create:precision_mechanism', N: 'kubejs:magnetized_needle'
  }).id('sborka:compass')
})
