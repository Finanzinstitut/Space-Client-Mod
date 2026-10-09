package gg.spaceclient.build

/**
 * Turns the one source tree into the source for one Minecraft version.
 *
 * Code that differs between versions sits in blocks:
 *
 * <pre>
 * //#if MC >= 26.3
 * code for 26.3 and later
 * //#elseif MC >= 26.1
 * //$$ code for 26.1 and 26.2
 * //#else
 * //$$ code for 1.21.11
 * //#endif
 * </pre>
 *
 * The newest version's code is written out plainly, so the tree reads and
 * compiles as that version; the others are kept behind "//$$ ". For a given
 * version, the block whose condition holds is switched on (its "//$$ " taken
 * off) and every other block is blanked. Lines are blanked rather than
 * dropped, so a compiler error in the generated file points at the same line
 * number in the real one.
 *
 * Conditions compare MC with a version using == != < <= > >=, joined by
 * && or ||. Versions compare part by part as numbers, so 1.21.11 < 26.1.
 */
class Preprocessor {

    static final String ACTIVE_PREFIX = '//$$'

    static String rename(String line, List<List> renames) {
        String out = line
        for (def rule : renames) out = ((java.util.regex.Pattern) rule[0]).matcher(out).replaceAll((String) rule[1])
        return out
    }

    static List<Integer> parse(String version) {
        version.trim().split('\\.').collect { it as int }
    }

    static int compare(List<Integer> a, List<Integer> b) {
        int n = Math.max(a.size(), b.size())
        for (int i = 0; i < n; i++) {
            int x = i < a.size() ? a[i] : 0
            int y = i < b.size() ? b[i] : 0
            if (x != y) return x <=> y
        }
        return 0
    }

    static boolean test(String condition, List<Integer> mc, String where) {
        String text = condition.trim()
        if (text.contains('||')) return text.split('\\|\\|').any { test(it, mc, where) }
        if (text.contains('&&')) return text.split('&&').every { test(it, mc, where) }
        def m = text =~ /^MC\s*(==|!=|<=|>=|<|>)\s*([0-9.]+)$/
        if (!m.matches()) throw new IllegalArgumentException("Cannot read condition '${condition}' at ${where}")
        int c = compare(mc, parse(m.group(2)))
        switch (m.group(1)) {
            case '==': return c == 0
            case '!=': return c != 0
            case '<': return c < 0
            case '<=': return c <= 0
            case '>': return c > 0
            case '>=': return c >= 0
        }
        throw new IllegalStateException()
    }

    /** Returns the text of one file for the given version. */
    static String process(String text, String version, String where) {
        return process(text, version, where, [])
    }

    /**
     * Reads a version's rename table: one rule per line, a regular expression
     * and its replacement separated by " => ". Blank lines and lines starting
     * with # are ignored. Used for names that changed everywhere at once -
     * a class or method renamed across the whole game - where a block in
     * every file would only add noise.
     */
    static List<List> readRenames(File file) {
        if (file == null || !file.isFile()) return []
        def rules = []
        file.eachLine('UTF-8') { line ->
            def t = line.trim()
            if (t.isEmpty() || t.startsWith('#')) return
            def parts = t.split(' => ', 2)
            if (parts.length != 2) throw new IllegalArgumentException("Bad rename rule: ${line}")
            rules << [java.util.regex.Pattern.compile(parts[0].trim()), parts[1].trim()]
        }
        return rules
    }

    static String process(String text, String version, String where, List<List> renames) {
        List<Integer> mc = parse(version)
        // One frame per open #if: whether its chosen branch is on, whether a
        // branch was already taken, and whether the whole block is reachable
        Deque<Map> stack = new ArrayDeque<>()
        boolean active = true
        def out = new StringBuilder()
        int number = 0
        String newline = text.contains('\r\n') ? '\r\n' : '\n'
        def lines = text.split('\r?\n', -1)
        for (String line : lines) {
            number++
            String trimmed = line.trim()
            String place = "${where}:${number}"
            if (trimmed.startsWith(ACTIVE_PREFIX) && trimmed.substring(ACTIVE_PREFIX.length()).trim().startsWith('//#')) {
                throw new IllegalArgumentException("Directive behind ${ACTIVE_PREFIX} at ${place}: directives are never prefixed")
            }
            if (trimmed.startsWith('//#if ')) {
                boolean outer = active
                boolean hit = outer && test(trimmed.substring(6), mc, place)
                stack.push([outer: outer, taken: hit])
                active = hit
                out.append(newline)
            } else if (trimmed.startsWith('//#elseif ')) {
                if (stack.isEmpty()) throw new IllegalArgumentException("#elseif without #if at ${place}")
                def frame = stack.peek()
                boolean hit = frame.outer && !frame.taken && test(trimmed.substring(10), mc, place)
                if (hit) frame.taken = true
                active = hit
                out.append(newline)
            } else if (trimmed == '//#else') {
                if (stack.isEmpty()) throw new IllegalArgumentException("#else without #if at ${place}")
                def frame = stack.peek()
                boolean hit = frame.outer && !frame.taken
                if (hit) frame.taken = true
                active = hit
                out.append(newline)
            } else if (trimmed == '//#endif') {
                if (stack.isEmpty()) throw new IllegalArgumentException("#endif without #if at ${place}")
                def frame = stack.pop()
                active = frame.outer
                out.append(newline)
            } else if (!active) {
                out.append(newline)
            } else {
                int at = line.indexOf(ACTIVE_PREFIX)
                if (at >= 0 && line.substring(0, at).trim().isEmpty()) {
                    String rest = line.substring(at + ACTIVE_PREFIX.length())
                    if (rest.startsWith(' ')) rest = rest.substring(1)
                    out.append(rename(line.substring(0, at) + rest, renames)).append(newline)
                } else {
                    out.append(rename(line, renames)).append(newline)
                }
            }
        }
        if (!stack.isEmpty()) throw new IllegalArgumentException("Unclosed #if in ${where}")
        // Every line was written with a newline after it; the original has one
        // fewer, between the last two elements of the split
        if (out.length() >= newline.length()) out.setLength(out.length() - newline.length())
        return out.toString()
    }
}
