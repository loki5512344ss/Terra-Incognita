// Промежуточные предметы Terra Incognita: нужны для усложнённых крафтов машин TFMG (см. server_scripts/tfmg_gates.js).
// Названия — в assets/kubejs/lang (en_us, ru_ru).
StartupEvents.registry('item', event => {
  event.create('refractory_lining')
  event.create('thermocouple')
  event.create('pressure_gauge')

  // Промежуточные стадии sequenced assembly
  event.create('incomplete_refractory_lining', 'create:sequenced_assembly').texture('kubejs:item/refractory_lining')
  event.create('incomplete_thermocouple', 'create:sequenced_assembly').texture('kubejs:item/thermocouple')
  event.create('incomplete_pressure_gauge', 'create:sequenced_assembly').texture('kubejs:item/pressure_gauge')
})
