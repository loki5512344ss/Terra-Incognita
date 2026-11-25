// Immersive Engineering tier 4 (electricity): wires, connectors, transformers, capacitors, dynamo.
// These gate on Create Additions electricity (copper coil, brass) so IE is the "heavy" grid,
// Create Additions is the "light" local grid.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('immersiveengineering')) return
  const redo = (output, count, pattern, key, id) => {
    event.remove({ output: output })
    event.shaped(Item.of(output, count), pattern, key).id('sborka:' + id)
  }

  // Copper wire coil: needs Create Additions copper wire
  redo('immersiveengineering:wirecoil_copper', 4, ['WWW', 'WCW', 'WWW'], {
    W: 'createaddition:copper_wire', C: 'create:copper_sheet'
  }, 'ie_wirecoil_copper')

  // Connectors need brass + electron tube
  redo('immersiveengineering:connector_lv', 4, [' B ', 'BEB', ' B '], {
    B: 'create:brass_sheet', E: 'create:electron_tube'
  }, 'ie_connector_lv')

  // Dynamo: the source of IE power, needs a motor-like build
  redo('immersiveengineering:dynamo', 1, ['CBC', 'SRS', 'CBC'], {
    C: 'immersiveengineering:wirecoil_copper', B: 'create:brass_casing',
    S: 'createaddition:electric_motor', R: 'minecraft:redstone_block'
  }, 'ie_dynamo')
})
