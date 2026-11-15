// Предметы, вырезанные из сборки. Один список на всё:
//  - server_scripts/steel.js и oil.js убирают их рецепты,
//  - client_scripts/hide.js прячет их в JEI.
global.terraRemoved = {
  // Сталь IE: слиток и пыль больше не получить, доменные печи IE не нужны (сталь только от TFMG)
  ieSteel: ['ingot_steel', 'dust_steel', 'blast_furnace', 'advanced_blast_furnace', 'blastbrick',
    'blastbrick_reinforced', 'blastfurnace_preheater'].map(id => 'immersiveengineering:' + id),
  // Нефтяные машины IE (нефть только от TFMG)
  ieOil: ['refinery', 'diesel_generator', 'fermenter'].map(id => 'immersiveengineering:' + id),
  // Добыча и перегонка нефти Create Diesel Generators (двигатели остаются)
  cdgOil: ['pumpjack_bearing', 'pumpjack_crank', 'pumpjack_head', 'pumpjack_hole',
    'distillation_controller', 'oil_scanner'].map(id => 'createdieselgenerators:' + id)
}
