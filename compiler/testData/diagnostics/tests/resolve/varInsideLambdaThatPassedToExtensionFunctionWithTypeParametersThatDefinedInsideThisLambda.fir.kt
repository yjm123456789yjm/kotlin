// WITH_STDLIB
// ISSUE: KT-52197

fun <K, V> helper(@<!OPT_IN_USAGE_ERROR!>BuilderInference<!> builderAction: MutableMap<K, V>.() -> Unit) {
    builderAction(mutableMapOf())
}

fun test(){
    helper {
        val x = put("key", "value")
        if (x != null) {
            "Error: $x"
            x.<!UNRESOLVED_REFERENCE!>length<!>
        }
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}
