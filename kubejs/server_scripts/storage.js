// Storage mods, split by tier. Only the recipes listed here are changed, the rest stay as the mods ship them.
ServerEvents.recipes(event => {
  // --- Storage Drawers (tier 1): an andesite casing replaces the wooden chest in every drawer ---
  if (Platform.isLoaded('storagedrawers')) {
    event.replaceInput({ mod: 'storagedrawers' }, '#c:chests/wooden', 'create:andesite_casing')
  }

  // --- Tom's Storage: the terminal is network storage and needs a circuit board (tier 4) ---
  if (Platform.isLoaded('toms_storage') && Platform.isLoaded('oc2r')) {
    event.remove({ output: 'toms_storage:storage_terminal' })
    event.shaped('toms_storage:storage_terminal', [' G ', 'GCG', ' B '], {
      G: '#c:glass_blocks', C: 'oc2r:circuit_board', B: 'create:brass_casing'
    }).id('sborka:toms_terminal')
  }

  // --- Sophisticated Backpacks (tier 2): a copper sheet replaces the chest ---
  if (Platform.isLoaded('sophisticatedbackpacks')) {
    event.replaceInput({ id: 'sophisticatedbackpacks:backpack' }, '#c:chests/wooden', 'create:copper_sheet')
  }
})
