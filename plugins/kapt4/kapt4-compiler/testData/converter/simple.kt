class Some<T, K : T> : B(), A<K> where T : A<String>, T : B

interface A<R>
abstract class B
