ruleset {

    description '''
        The whole analysis this project runs on itself: the curated selection of CodeNarc stock
        rules this repository previously kept in .codenarc.groovy, plus every rule this artifact
        defines. One reference, no composition to maintain.

        Unlike rulesets/groovy/joke.groovy, this file names rules it does not own. A stock rule
        renamed or removed by a later CodeNarc is a ruleset-load failure here and nowhere else, so
        this file carries a narrower support window than the artifact does. That window is a stated
        promise, not an enforced constraint: no task, guard or version list in the build asserts it.

        The selection is an allowlist of individually named rules rather than whole stock rulesets
        with exclusions. An allowlist adopts no rule a future CodeNarc adds, which is the safer
        default for a file whose every name is a potential load failure.

        To compose the stock rules differently, reference rulesets/groovy/joke.groovy instead and
        bring your own composition.
    '''


//    Braces
    ElseBlockBraces
    ForStatementBraces
    IfStatementBraces
    WhileStatementBraces

//    Convention
    ConfusingTernary
    CouldBeElvis
    CouldBeSwitchStatement
    HashtableIsObsolete
    IfStatementCouldBeTernary
    InvertedCondition
    InvertedIfElse
    LongLiteralWithLowerCaseL
    NoDouble
    NoFloat
    NoJavaUtilDate
    NoTabCharacter
    ParameterReassignment
    PublicMethodsBeforeNonPublicMethods
    StaticFieldsBeforeInstanceFields
    StaticMethodsBeforeInstanceMethods
    TernaryCouldBeElvis
    VectorIsObsolete

//    Groovyism
    AssignCollectionSort
    AssignCollectionUnique
    ClosureAsLastMethodParameter
    CollectAllIsDeprecated
    ConfusingMultipleReturns
    ExplicitArrayListInstantiation
    ExplicitCallToAndMethod
    ExplicitCallToCompareToMethod
    ExplicitCallToDivMethod
    ExplicitCallToEqualsMethod
    ExplicitCallToGetAtMethod
    ExplicitCallToLeftShiftMethod
    ExplicitCallToMinusMethod
    ExplicitCallToModMethod
    ExplicitCallToMultiplyMethod
    ExplicitCallToOrMethod
    ExplicitCallToPlusMethod
    ExplicitCallToPowerMethod
    ExplicitCallToPutAtMethod
    ExplicitCallToRightShiftMethod
    ExplicitCallToXorMethod
    ExplicitHashMapInstantiation
    ExplicitHashSetInstantiation
    ExplicitLinkedHashMapInstantiation
    ExplicitLinkedListInstantiation
    ExplicitStackInstantiation
    ExplicitTreeSetInstantiation
    GStringAsMapKey
    GStringExpressionWithinString
    GetterMethodCouldBeProperty
    GroovyLangImmutable
    UseCollectMany
    UseCollectNested

//    Imports
    DuplicateImport
    UnnecessaryGroovyImport
    UnusedImport

//    Junit
    SpockIgnoreRestUsed
    SpockMissingAssert


//    Unnecessary
    AddEmptyString
    ConsecutiveLiteralAppends
    ConsecutiveStringConcatenation
    UnnecessaryBigDecimalInstantiation
    UnnecessaryBigIntegerInstantiation
    UnnecessaryBooleanExpression
    UnnecessaryBooleanInstantiation
    UnnecessaryCallForLastElement
    UnnecessaryCallToSubstring
    UnnecessaryCast
    UnnecessaryCatchBlock
    UnnecessaryCollectCall
    UnnecessaryCollectionCall
    UnnecessaryConstructor
    UnnecessaryDefInFieldDeclaration
    UnnecessaryDefInMethodDeclaration
    UnnecessaryDefInVariableDeclaration
    UnnecessaryDotClass
    UnnecessaryDoubleInstantiation
    UnnecessaryElseStatement
    UnnecessaryFinalOnPrivateMethod
    UnnecessaryFloatInstantiation
    UnnecessaryGString
    UnnecessaryGetter
    UnnecessaryIfStatement
    UnnecessaryInstanceOfCheck
    UnnecessaryInstantiationToGetClass
    UnnecessaryIntegerInstantiation
    UnnecessaryLongInstantiation
    UnnecessaryModOne
    UnnecessaryNullCheck
    UnnecessaryNullCheckBeforeInstanceOf
//    UnnecessaryObjectReferences
    UnnecessaryOverridingMethod
    UnnecessaryPackageReference
    UnnecessaryParenthesesForMethodCallWithClosure
    UnnecessaryPublicModifier
    UnnecessaryReturnKeyword
    UnnecessarySafeNavigationOperator
    UnnecessarySelfAssignment
    UnnecessarySemicolon
    UnnecessarySetter
    UnnecessaryStringInstantiation
    UnnecessaryTernaryExpression
    UnnecessaryToString
    UnnecessaryTransientModifier

//    Unused
    UnusedArray
    UnusedMethodParameter
    UnusedObject
    UnusedPrivateField
    UnusedPrivateMethod
    UnusedPrivateMethodParameter
    UnusedVariable

//    This artifact's own rules
    ruleset('rulesets/groovy/joke.groovy')
}
