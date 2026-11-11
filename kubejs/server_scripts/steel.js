// Одна сталь на всю сборку: сталь делает только TFMG (доменная печь -> расплав -> литейная форма).
// Сталь IE остаётся в реестре, но больше нигде не крафтится; все рецепты, где нужна сталь, принимают тег c:ingots/steel.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // Источники стали IE
  event.remove({ output: 'immersiveengineering:ingot_steel' })
  event.remove({ output: 'immersiveengineering:dust_steel' })

  // Доменные печи IE больше не нужны: без них сталь IE не сделать
  const dead = ['blast_furnace', 'advanced_blast_furnace', 'blastbrick', 'blastbrick_reinforced', 'blastfurnace_preheater']
  dead.forEach(id => event.remove({ output: 'immersiveengineering:' + id }))

  // Рецепты IE, которые просили именно свой слиток, теперь принимают любую сталь (то есть TFMG)
  event.replaceInput({ mod: 'immersiveengineering' }, 'immersiveengineering:ingot_steel', '#c:ingots/steel')
})
