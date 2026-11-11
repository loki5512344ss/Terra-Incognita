// Одна нефть: добыча и перегонка только из TFMG (качалка -> перегонка -> дизель/бензин/керосин).
//  - Нефтяные машины IE убраны. Immersive Petroleum вырезан из сборки целиком (jar отключён).
//  - Create Diesel Generators остаётся ради двигателей: они топятся тегами c:diesel / c:gasoline,
//    в которые TFMG уже добавляет свои жидкости, так что двигатели работают на топливе TFMG без доработок.
//    Свою добычу и перегонку нефти CDG теряет; биотопливо (этанол, растительное масло, биодизель) остаётся.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // Immersive Engineering
  const ieOil = ['refinery', 'diesel_generator', 'fermenter']
  ieOil.forEach(id => event.remove({ output: 'immersiveengineering:' + id }))

  // Create Diesel Generators: убираем добычу и перегонку нефти (есть у TFMG)
  const cdgOil = [
    'pumpjack_bearing', 'pumpjack_crank', 'pumpjack_head', 'pumpjack_hole',
    'distillation_controller', 'oil_scanner'
  ]
  cdgOil.forEach(id => event.remove({ output: 'createdieselgenerators:' + id }))
  event.remove({ type: 'createdieselgenerators:distillation' })
})

// Месторождения нефти CDG в мире отключаем: пустой тег биомов (нефть генерирует TFMG)
ServerEvents.tags('worldgen/biome', event => {
  if (!Platform.isLoaded('tfmg')) return
  event.removeAll('createdieselgenerators:oil_biomes')
})
