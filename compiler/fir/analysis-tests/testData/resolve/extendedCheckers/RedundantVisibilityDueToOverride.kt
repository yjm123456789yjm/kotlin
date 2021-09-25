abstract class A {
    open var attribute = "a"
        protected set
}

class C1 : A() {
    public override var attribute = super.attribute // REDUNDANT_VISIBILITY_MODIFIER is not reported
        public set
}

abstract class B2 : A() {
    override var attribute = "b"
        public set
}

class C2 : B2() {
    public override var attribute = super.attribute // REDUNDANT_VISIBILITY_MODIFIER is not reported
}
