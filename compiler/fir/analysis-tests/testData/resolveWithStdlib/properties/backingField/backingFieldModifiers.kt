class A {
    val a: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>abstract<!> field = 1

    val b: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>open<!> field = 1

    val c: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>final<!> field = 1

    val d: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>inline<!> field = 1

    val e: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>noinline<!> field = 1

    val f: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>crossinline<!> field = 1

    val g: Number
        <!INAPPLICABLE_BACKING_FIELD_MODIFIER, WRONG_MODIFIER_TARGET!>tailrec<!> field = 1
}
