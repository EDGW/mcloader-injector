package com.kako.chan.mcloader.injector.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Sole parser for agent arguments and forwarding system properties. */
final class RuleTable {
    private static final String TABLE = "mcloader.injector.table";
    private static final String RULE = "mcloader.injector.rule";
    final String[] from;
    final String[] to;

    private RuleTable(List<String[]> rules) {
        rules.sort(Comparator.<String[]>comparingInt(rule -> rule[0].length()).reversed()
                .thenComparing(rule -> rule[0]));
        from = new String[rules.size()];
        to = new String[rules.size()];
        for (int i = 0; i < rules.size(); i++) {
            from[i] = rules.get(i)[0];
            to[i] = rules.get(i)[1];
        }
    }

    static RuleTable parse(String agentArgs, Properties properties) {
        List<String> keys = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals(TABLE) || key.equals(RULE) || key.startsWith(RULE + ".")) keys.add(key);
        }
        keys.sort(String::compareTo);
        List<String> values = new ArrayList<>();
        if (keys.isEmpty()) {
            if (agentArgs == null) throw new IllegalArgumentException("No forwarding rules supplied");
            values.add(agentArgs);
        } else {
            for (String key : keys) values.add(properties.getProperty(key));
        }
        List<String[]> rules = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        for (String value : values) {
            for (String entry : value.split("\\|", -1)) {
                int separator = entry.indexOf("=>");
                if (separator < 0) throw new IllegalArgumentException("Expected from=>to rule: " + entry);
                String source = entry.substring(0, separator).trim();
                String target = entry.substring(separator + 2).trim();
                validate(source);
                validate(target);
                if (!sources.add(source)) throw new IllegalArgumentException("Duplicate rule prefix: " + source);
                rules.add(new String[] {source, target});
            }
        }
        return new RuleTable(rules);
    }

    private static void validate(String value) {
        try {
            URI uri = new URI(value);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("Rule prefix must be an absolute HTTP(S) URL: " + value);
            }
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid rule URL: " + value, error);
        }
    }
}
