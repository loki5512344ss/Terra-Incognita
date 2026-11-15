// Одна нефть: добыча и перегонка только из TFMG (качалка -> перегонка -> дизель/бензин/керосин).
//  - Нефтяные машины IE убраны. Immersive Petroleum вырезан из сборки целиком (jar отключён).
//  - Create Diesel Generators остаётся ради двигателей: они топятся тегами c:diesel / c:gasoline,
//    в которые TFMG уже добавляет свои жидкости, так что двигатели работают на топливе TFMG без доработок.
//    Свою добычу и перегонку нефти CDG теряет; биотопливо (этанол, растительное масло, биодизель) остаётся.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // Машины из списка startup_scripts/removed.js: нефтяные IE и добыча/перегонка CDG (есть у TFMG)
  global.terraRemoved.ieOil.concat(global.terraRemoved.cdgOil).forEach(id => event.remove({ output: id }))
  event.remove({ type: 'createdieselgenerators:distillation' })
})

// Месторождения нефти CDG в мире отключаем: пустой тег биомов (нефть генерирует TFMG)
ServerEvents.tags('worldgen/biome', event => {
  if (!Platform.isLoaded('tfmg')) return
  event.removeAll('createdieselgenerators:oil_biomes')
})
