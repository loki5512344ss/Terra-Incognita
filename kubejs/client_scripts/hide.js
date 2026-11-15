// Прячет в JEI предметы, которые в сборке больше не крафтятся (список — startup_scripts/removed.js).
RecipeViewerEvents.removeEntries('item', event => {
  if (!Platform.isLoaded('tfmg')) return
  const r = global.terraRemoved
  r.ieSteel.concat(r.ieOil, r.cdgOil).forEach(id => event.remove(id))
})
