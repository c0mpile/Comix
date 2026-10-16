package com.c0mpile.comix.data.archive

/**
 * Natural alphanumeric comparator for filenames and archive entry names.
 *
 * Chunks of digits are compared numerically so that e.g. "page2.jpg" sorts before
 * "page10.jpg" and "01.png" sorts before "02.png". Non-digit chunks are compared
 * case-insensitively, with deterministic case-sensitive tie-breaking.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(s1: String?, s2: String?): Int {
        if (s1 === s2) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        var i = 0
        var j = 0
        val len1 = s1.length
        val len2 = s2.length

        while (i < len1 && j < len2) {
            val c1 = s1[i]
            val c2 = s2[j]

            if (c1.isDigit() && c2.isDigit()) {
                val start1 = i
                while (i < len1 && s1[i].isDigit()) {
                    i++
                }
                val start2 = j
                while (j < len2 && s2[j].isDigit()) {
                    j++
                }

                val num1 = s1.substring(start1, i)
                val num2 = s2.substring(start2, j)

                var nz1 = 0
                while (nz1 < num1.length && num1[nz1] == '0') {
                    nz1++
                }
                var nz2 = 0
                while (nz2 < num2.length && num2[nz2] == '0') {
                    nz2++
                }

                val sig1 = num1.substring(nz1)
                val sig2 = num2.substring(nz2)

                val lengthDiff = sig1.length - sig2.length
                if (lengthDiff != 0) {
                    return lengthDiff
                }

                val cmp = sig1.compareTo(sig2)
                if (cmp != 0) {
                    return cmp
                }

                val lenDiff = num1.length - num2.length
                if (lenDiff != 0) {
                    return lenDiff
                }
            } else {
                val f1 = c1.lowercaseChar()
                val f2 = c2.lowercaseChar()
                if (f1 != f2) {
                    return f1.compareTo(f2)
                }
                i++
                j++
            }
        }

        val remainingDiff = (len1 - i) - (len2 - j)
        if (remainingDiff != 0) {
            return remainingDiff
        }

        return s1.compareTo(s2)
    }
}
