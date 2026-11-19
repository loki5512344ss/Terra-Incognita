// Items cut from the pack. One list for everything:
//  - server_scripts/steel.js and oil.js remove their recipes,
//  - client_scripts/hide.js hides them in JEI.
global.terraRemoved = {
  // IE steel: the ingot and dust are unobtainable, IE blast furnaces are not needed (steel comes only from TFMG)
  ieSteel: ['ingot_steel', 'dust_steel', 'blast_furnace', 'advanced_blast_furnace', 'blastbrick',
    'blastbrick_reinforced', 'blastfurnace_preheater'].map(id => 'immersiveengineering:' + id),
  // IE oil machines (oil comes only from TFMG)
  ieOil: ['refinery', 'diesel_generator', 'fermenter'].map(id => 'immersiveengineering:' + id),
  // Create Diesel Generators oil extraction and refining (the engines stay)
  cdgOil: ['pumpjack_bearing', 'pumpjack_crank', 'pumpjack_head', 'pumpjack_hole',
    'distillation_controller', 'oil_scanner'].map(id => 'createdieselgenerators:' + id)
}
