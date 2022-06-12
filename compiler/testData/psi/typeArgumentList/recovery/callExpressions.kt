fun f() {
    call<>
    call<x>
    call<x.>
    call<>()
    call<x>()
    call<x.>()
    call<> { }
    call<x> { }
    call<x.> { }
    call<>[]
    call<x>[]
    call<x.>[]
    call<>::
    call<x>::
    call<x.>::
    call<>[]
    call<x>[]
    call<x.>[]
    call<>?
    call<x>?
    call<x.>?
    call<>?.
    call<x>?.
    call<x.>?.
    call<>.
    call<x>.
    call<x.>.
    call<>++
    call<x>++
    call<x.>++
    call<>--
    call<x>--
    call<x.>--
    call<>!!
    call<x>!!
    call<x.>!!

    a(call<>())
    a(call<x>())
    a(call<x.>())
    a(call<> { })
    a(call<x> { })
    a(call<x.> { })
    a(call<>::)
    a(call<x>::)
    a(call<x.>::)
    a(call<>[])
    a(call<x>[])
    a(call<x.>[])
    a(call<>?)
    a(call<x>?)
    a(call<x.>?)
    a(call<>?.)
    a(call<x>?.)
    a(call<x.>?.)
    a(call<>.)
    a(call<x>.)
    a(call<x.>.)
    a(call<>++)
    a(call<x>++)
    a(call<x.>++)
    a(call<>--)
    a(call<x>--)
    a(call<x.>--)
    a(call<>!!)
    a(call<x>!!)
    a(call<x.>!!)
}