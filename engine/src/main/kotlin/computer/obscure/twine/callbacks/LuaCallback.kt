package computer.obscure.twine.callbacks

// this is some of the greatest code ever written
// obviously this is HORRIBLE, but I didn't want to use reflection and
// this is the simplest solution I could think of
// supports up to 22 arguments, but if you're using more than that
// please re-evaluate your code
class LuaCallback(private val call: (Array<out Any?>) -> Any?) :
    () -> Any?,
    (Any?) -> Any?,
    (Any?, Any?) -> Any?,
    (Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?,
    (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any? {

    override fun invoke(): Any? = call(NO_ARGS)
    override fun invoke(a: Any?): Any? = call(arrayOf(a))
    override fun invoke(a: Any?, b: Any?): Any? = call(arrayOf(a, b))
    override fun invoke(a: Any?, b: Any?, c: Any?): Any? = call(arrayOf(a, b, c))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?): Any? = call(arrayOf(a, b, c, d))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?): Any? = call(arrayOf(a, b, c, d, e))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?): Any? = call(arrayOf(a, b, c, d, e, f))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?, r: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?, r: Any?, s: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?, r: Any?, s: Any?, t: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?, r: Any?, s: Any?, t: Any?, u: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u))
    override fun invoke(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?, i: Any?, j: Any?, k: Any?, l: Any?, m: Any?, n: Any?, o: Any?, p: Any?, q: Any?, r: Any?, s: Any?, t: Any?, u: Any?, v: Any?): Any? = call(arrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))

    private companion object {
        val NO_ARGS = emptyArray<Any?>()
    }
}