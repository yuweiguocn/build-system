package test.pkg

@Suppress("UNUSED_PARAMETER", "unused", "UNUSED_VARIABLE")
class AccessTest2 {

    private val field1: Int = 0
    internal var field2: Int = 0
    var field3: Int = 0
    private val field4 = 100
    private val field5 = arrayOfNulls<Inner>(100)


    private constructor()

    internal constructor(x: Int)

    private fun method1() {
        val f = field1 // OK - private but same class
    }

    internal fun method2() {
        method1() // OK - private but same class
    }

    fun method3() {}

    internal inner class Inner {
        private fun innerMethod() {
            AccessTest2() // ERROR
            AccessTest2(42) // OK - package private

            val f1 = field1 // ERROR
            val f2 = field2 // OK - package private
            val f3 = field3 // OK - public
            val f4 = field4 // OK (constants inlined)
            val f5 = field5 // OK - array

            method1() // ERROR
            method2() // OK - package private
            method3() // OK - public
        }

        private fun testSuppress() {
            //noinspection SyntheticAccessor
            method1() // OK - suppressed
            //noinspection PrivateMemberAccessBetweenOuterAndInnerClass
            method1() // OK - suppressed with IntelliJ similar inspection id
            //noinspection SyntheticAccessorCall
            method1() // OK - suppressed with IntelliJ similar inspection id
        }
    }

    fun viaAnonymousInner() {
        val btn = object : Any() {
            fun method4() {
                AccessTest2()   // ERROR
                AccessTest2(42) // OK - package private

                val f1 = field1 // ERROR
                val f2 = field2 // OK - package private
                val f3 = field3 // OK - public
                val f4 = field4 // OK (constants inlined)
                val f5 = field5 // OK - array

                method1() // ERROR
                method2() // OK - package private
                method3() // OK - public
            }
        }
    }
}

