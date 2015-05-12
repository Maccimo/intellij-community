// PARAM_TYPES: A.B<U>
// PARAM_TYPES: V, Data
// PARAM_DESCRIPTOR: internal final inner class B<U : Data> where U : DataExEx defined in A
// PARAM_DESCRIPTOR: value-parameter val v: V defined in A.B.foo
open class Data(val x: Int)
interface DataEx
interface DataExEx

class A<T: Data>(val t: T) where T: DataEx {
    // SIBLING:
    inner class B<U: Data>(val u: U) where U: DataExEx {
        fun foo<V: Data>(v: V): Int where V: DataEx {
            return <selection>t.x + u.x + v.x</selection>
        }
    }
}