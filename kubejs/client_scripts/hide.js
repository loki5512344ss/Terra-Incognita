// Hides items in JEI that can no longer be crafted in the pack (list in startup_scripts/removed.js).
RecipeViewerEvents.removeEntries('item', event => {
  if (!Platform.isLoaded('tfmg')) return
  const r = global.terraRemoved
  r.ieSteel.concat(r.ieOil, r.cdgOil).forEach(id => event.remove(id))
})
