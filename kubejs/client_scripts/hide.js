// Hides items in JEI that can no longer be crafted in the pack (list in startup_scripts/removed.js).
RecipeViewerEvents.removeEntries('item', event => {
  const r = global.terraRemoved
  r.ieSteel.concat(r.ieOil, r.cdgOil, r.suppGlobes, r.noTexture).forEach(id => event.remove(id))
})
