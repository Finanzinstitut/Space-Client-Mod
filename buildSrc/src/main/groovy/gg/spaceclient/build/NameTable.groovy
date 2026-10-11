package gg.spaceclient.build

/**
 * The runtime names of the game's classes, methods and fields that the mod
 * names in strings, for versions that ship obfuscated (1.21.11).
 *
 * The mod finds some of the game's members by name at runtime. In the
 * development environment, and on 26.1 and later, those names are Mojang's
 * own; in a real 1.21.11 game they are Fabric's intermediary names
 * (class_442, method_1531, ...). A lookup by Mojang name would silently find
 * nothing there. So at build time every string literal in the source that is
 * a Mojang name of something in the game is written into a small table with
 * its intermediary names, and the mod's reflection helpers consult it.
 *
 * Output lines: "c <tab> named.Class <tab> runtime.Class", and
 * "m|f <tab> name <tab> runtimeName" (one line per possible runtime name).
 */
class NameTable {

    static String build(File tinyMappings, Collection<File> sources) {
        Set<String> wanted = new HashSet<>()
        def literal = ~/"([A-Za-z_$][A-Za-z0-9_$.\/]*)"/
        sources.each { file ->
            def m = literal.matcher(file.getText('UTF-8'))
            while (m.find()) {
                String s = m.group(1)
                wanted << s
                wanted << s.replace('/', '.')
            }
        }
        def out = new StringBuilder()
        Set<String> seen = new HashSet<>()
        int iNamed = -1, iInter = -1
        tinyMappings.eachLine('UTF-8') { line ->
            def parts = line.split('\t', -1)
            if (parts[0] == 'tiny') {
                def ns = parts[3..-1]
                iNamed = ns.indexOf('named'); iInter = ns.indexOf('intermediary')
                return
            }
            if (parts[0] == 'c') {
                String inter = parts[1 + iInter].replace('/', '.')
                String named = parts[1 + iNamed].replace('/', '.')
                String simple = named.substring(named.lastIndexOf('.') + 1)
                String nested = simple.contains('$') ? simple.substring(simple.lastIndexOf('$') + 1) : simple
                if (wanted.contains(named) || wanted.contains(simple) || wanted.contains(nested)) {
                    String key = "c\t${named}\t${inter}"
                    if (seen.add(key)) out << key << '\n'
                }
            } else if (parts.length > 3 && parts[0] == '' && (parts[1] == 'm' || parts[1] == 'f')) {
                // "\tm\tdesc\tofficial\tintermediary\tnamed"
                String inter = parts[3 + iInter]
                String named = parts[3 + iNamed]
                if (named != inter && wanted.contains(named)) {
                    String key = "${parts[1]}\t${named}\t${inter}"
                    if (seen.add(key)) out << key << '\n'
                }
            }
        }
        return out.toString()
    }
}
