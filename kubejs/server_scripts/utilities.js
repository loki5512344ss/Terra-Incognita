// Utilities: Supplementaries globes removed (they show coordinates), Exposure camera, Ribbits.
ServerEvents.recipes(event => {
  if (Platform.isLoaded('supplementaries')) {
    global.terraRemoved.suppGlobes.forEach(id => event.remove({ output: id }))
  }

  // --- Exposure (photo camera): optical lens + brass ---
  if (Platform.isLoaded('exposure')) {
    event.remove({ output: 'exposure:camera' })
    event.shaped('exposure:camera', ['BBB', 'BLB', 'BBB'], {
      B: 'create:brass_sheet', L: 'kubejs:optical_lens'
    }).id('sborka:exposure_camera')
  }

  // --- Ribbits: their iron parts use sheets ---
  if (Platform.isLoaded('ribbits')) {
    event.replaceInput({ mod: 'ribbits' }, 'minecraft:iron_ingot', 'create:iron_sheet')
  }
})
