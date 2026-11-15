// Одна сталь на всю сборку: сталь делает только TFMG (доменная печь -> расплав -> литейная форма).
// Сталь IE остаётся в реестре, но больше нигде не крафтится; все рецепты, где нужна сталь, принимают тег c:ingots/steel.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('tfmg')) return

  // Слиток и пыль стали IE и доменные печи IE (список — startup_scripts/removed.js)
  global.terraRemoved.ieSteel.forEach(id => event.remove({ output: id }))

  // Рецепты IE, которые просили именно свой слиток, теперь принимают любую сталь (то есть TFMG)
  event.replaceInput({ mod: 'immersiveengineering' }, 'immersiveengineering:ingot_steel', '#c:ingots/steel')
})
