package com.covdbg.coverage.run

/**
 * Reassembles whole lines from process output, which arrives in arbitrary chunks.
 *
 * Both streams are buffered separately, a trailing `\r` is dropped, and [flush] emits a final
 * unterminated line. Lines are truncated at [MAX_LINE_LENGTH] and the pending buffer is capped, so a
 * target that prints a huge blob - or binary, with no newline at all - cannot grow the heap.
 */
internal class LineAssembler(private val onLine: (line: String, isError: Boolean) -> Unit) {

    private val out = StringBuilder()
    private val err = StringBuilder()

    fun accept(text: String, isError: Boolean) {
        val buffer = if (isError) err else out
        buffer.append(text)
        // Walk the complete lines, then drop them in one go: deleting after each line would shift the
        // rest of the buffer every time, and this sees all of the target's output.
        var start = 0
        while (true) {
            val newline = buffer.indexOf("\n", start)
            if (newline < 0) break
            emit(buffer.substring(start, newline).removeSuffix("\r"), isError)
            start = newline + 1
        }
        if (start > 0) buffer.delete(0, start)
        // No newline in sight and the buffer is already longer than any line we care about: emit
        // what we have rather than accumulating without limit.
        if (buffer.length > MAX_LINE_LENGTH) {
            emit(buffer.toString(), isError)
            buffer.setLength(0)
        }
    }

    fun flush() {
        if (out.isNotEmpty()) {
            emit(out.toString(), false)
            out.setLength(0)
        }
        if (err.isNotEmpty()) {
            emit(err.toString(), true)
            err.setLength(0)
        }
    }

    private fun emit(line: String, isError: Boolean) = onLine(line.take(MAX_LINE_LENGTH), isError)

    private companion object {
        /** covdbg's markers are short, and so is the first line of any diagnostic we quote back. */
        const val MAX_LINE_LENGTH = 2_000
    }
}
