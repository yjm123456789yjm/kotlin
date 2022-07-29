// DUMP_CFG
// ISSUE: KT-53400

enum class Nested1 {
    WHITE {
        <!WRONG_MODIFIER_TARGET!>enum<!> class Nested2 {
            BLACK {
                <!WRONG_MODIFIER_TARGET!>enum<!> class Nested3 { RED }
            }
        }
    }
}
